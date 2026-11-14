package com.nazjara.brewery.inventory.internal;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingInt;
import static java.util.stream.Collectors.toMap;

import com.nazjara.brewery.order.LineItem;
import com.nazjara.brewery.order.OrderAllocated;
import com.nazjara.brewery.order.OrderAllocationFailed;
import com.nazjara.brewery.order.OrderCancelled;
import com.nazjara.brewery.order.OrderPlaced;

import java.util.Map;
import java.util.TreeMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Reserves stock for placed orders and releases it for cancelled ones.
 * <p>
 * {@code @ApplicationModuleListener} combines {@code @Async}, {@code @Transactional(REQUIRES_NEW)} and
 * {@code @TransactionalEventListener} (after commit). Each event is handled on a task-executor thread, in a new
 * transaction, only after the order transaction has committed, so the HTTP request that placed the order has usually
 * returned already. A failure here rolls back only this transaction: the order stays {@code NEW}, and without the
 * Event Publication Registry the event is neither stored nor retried.
 * <p>
 * The reply ({@link OrderAllocated} or {@link OrderAllocationFailed}) is published inside this listener's transaction,
 * so the order module sees it only if the reservation commits. Allocation is all-or-nothing: either every line is
 * reserved or nothing is, and the order is told which.
 * <p>
 * Listening to {@code order}'s events and publishing {@code order}'s reply types makes this module depend on
 * {@code order}, which {@code allowedDependencies} in this module's {@code package-info.java} permits. Replaces
 * {@code beer-inventory-service}'s JMS {@code AllocateOrderListener} and {@code DeallocateOrderListener}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OrderAllocationListener {

    private final StockItemRepository stock;
    private final AllocationRepository allocations;
    private final ApplicationEventPublisher events;

    @ApplicationModuleListener
    void on(OrderPlaced event) {
        if (allocations.existsByOrderId(event.orderId())) {
            log.info("Order {} is already allocated, ignoring repeated event", event.orderId());
            return;
        }

        var requested = event.lines().stream()
                .collect(groupingBy(LineItem::upc, TreeMap::new, summingInt(LineItem::quantity)));
        var items = lockStockOf(requested);
        var sufficient = requested.entrySet().stream().allMatch(line ->
                items.containsKey(line.getKey()) && items.get(line.getKey()).quantityOnHand() >= line.getValue());
        if (!sufficient) {
            log.info("Not enough stock for order {}", event.orderId());
            events.publishEvent(new OrderAllocationFailed(event.orderId()));
            return;
        }

        stock.saveAll(requested.entrySet().stream()
                .map(line -> items.get(line.getKey()).take(line.getValue()))
                .toList());
        allocations.save(Allocation.of(event.orderId(), requested.entrySet().stream()
                .map(line -> new AllocationLine(line.getKey(), line.getValue()))
                .toList()));
        log.info("Allocated order {}", event.orderId());
        events.publishEvent(new OrderAllocated(event.orderId()));
    }

    @ApplicationModuleListener
    void on(OrderCancelled event) {
        allocations.findByOrderId(event.orderId()).ifPresentOrElse(allocation -> {
            var released = allocation.lines().stream()
                    .collect(groupingBy(AllocationLine::upc, TreeMap::new, summingInt(AllocationLine::quantity)));
            var items = lockStockOf(released);
            stock.saveAll(released.entrySet().stream()
                    .map(line -> items.get(line.getKey()).putBack(line.getValue()))
                    .toList());
            allocations.delete(allocation);
            log.info("Released stock of cancelled order {}", event.orderId());
        }, () -> log.info("Order {} has no allocation, nothing to release", event.orderId()));
    }

    private Map<String, StockItem> lockStockOf(Map<String, Integer> quantitiesByUpc) {
        return stock.findByUpcInOrderByUpc(quantitiesByUpc.keySet()).stream()
                .collect(toMap(StockItem::upc, identity()));
    }
}
