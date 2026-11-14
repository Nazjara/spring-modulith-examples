package com.nazjara.brewery.order;

import java.util.List;

/**
 * Published by {@link OrderService#place} when an order has been stored. Listeners receive it only after the placing
 * transaction commits.
 * <p>
 * Replaces the {@code AllocateOrderRequest} that {@code beer-order-service} sent to {@code ALLOCATE_ORDER_QUEUE}.
 *
 * @param orderId identifier of the stored order
 * @param lines   ordered beers
 */
public record OrderPlaced(Long orderId, List<LineItem> lines) {

    public OrderPlaced {
        lines = List.copyOf(lines);
    }
}
