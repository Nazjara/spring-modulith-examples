package com.nazjara.brewery.order.internal;

import com.nazjara.brewery.order.OrderAllocated;
import com.nazjara.brewery.order.OrderAllocationFailed;
import com.nazjara.brewery.order.OrderStatus;

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
 * allocation stays committed, the Event Publication Registry marks the reply's publication {@code FAILED}, and
 * {@code EventPublicationMaintenance} resubmits it later.
 * <p>
 * Delivery through the registry is at-least-once: if the instance dies after this listener commits but before the
 * registry marks the publication completed, the reply arrives again. A reply for an order that is no longer
 * {@code NEW} has therefore already been applied and is ignored instead of failing on the status transition. The
 * order is read with {@code SELECT … FOR UPDATE}: a duplicate handled at the same time (a slow listener outliving its
 * staleness threshold and being resubmitted) waits for the first one to commit and then sees the new status, instead
 * of also reading {@code NEW} and later overwriting a cancellation made in between.
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
        var order = orders.findLockedById(orderId)
                .orElseThrow(() -> new IllegalStateException("Order %d not found".formatted(orderId)));
        if (order.status() != OrderStatus.NEW) {
            log.info("Order {} is already {}, ignoring repeated reply", orderId, order.status());
            return;
        }
        var updated = orders.save(transition.apply(order));
        log.info("Order {} is now {}", orderId, updated.status());
    }
}
