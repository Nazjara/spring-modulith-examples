package com.nazjara.brewery.order.internal;

import com.nazjara.brewery.order.OrderAllocated;
import com.nazjara.brewery.order.OrderAllocationFailed;

import java.util.function.UnaryOperator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Applies allocation replies to the order.
 * <p>
 * {@code @ApplicationModuleListener} combines {@code @Async}, {@code @Transactional(REQUIRES_NEW)} and
 * {@code @TransactionalEventListener} (after commit): each reply is handled on a task-executor thread, in a new
 * transaction, once the allocating transaction has committed. An exception here rolls back only the status update; the
 * allocation stays committed and, without the Event Publication Registry, the reply is not retried.
 * <p>
 * The parameter types belong to {@code order}'s own API, so listening adds no dependency on the module that publishes
 * them. Replaces {@code beer-order-service}'s JMS {@code AllocationResultListener} on
 * {@code ALLOCATE_ORDER_RESPONSE_QUEUE}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AllocationResultListener {

    private final BeerOrderRepository orders;

    @ApplicationModuleListener
    void on(OrderAllocated event) {
        update(event.orderId(), BeerOrder::markAllocated);
    }

    @ApplicationModuleListener
    void on(OrderAllocationFailed event) {
        update(event.orderId(), BeerOrder::markAllocationFailed);
    }

    private void update(long orderId, UnaryOperator<BeerOrder> transition) {
        var order = orders.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order %d not found".formatted(orderId)));
        var updated = orders.save(transition.apply(order));
        log.info("Order {} is now {}", orderId, updated.status());
    }
}
