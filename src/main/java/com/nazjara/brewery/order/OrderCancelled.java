package com.nazjara.brewery.order;

/**
 * Published by {@link OrderService#cancel} when an order has been cancelled. Stock reserved for the order is to be
 * released.
 * <p>
 * Replaces the {@code DeallocateOrderRequest} that {@code beer-order-service} sent to {@code DEALLOCATE_ORDER_QUEUE}.
 *
 * @param orderId identifier of the cancelled order
 */
public record OrderCancelled(Long orderId) {
}
