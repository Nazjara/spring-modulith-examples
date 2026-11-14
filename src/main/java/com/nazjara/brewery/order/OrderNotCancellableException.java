package com.nazjara.brewery.order;

/** Thrown when an order is cancelled in a status that does not allow it. */
public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(long orderId, OrderStatus status) {
        super("Order %d cannot be cancelled in status %s".formatted(orderId, status));
    }
}
