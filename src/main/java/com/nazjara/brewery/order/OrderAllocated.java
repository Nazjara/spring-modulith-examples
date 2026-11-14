package com.nazjara.brewery.order;

/**
 * Reply to {@link OrderPlaced}: stock for every line of the order has been reserved.
 * <p>
 * Declared by {@code order} but published by whichever module allocates stock. Because the order module owns both the
 * request and the reply types, the allocating module depends on {@code order} and {@code order} depends on nothing
 * for this conversation, so events flow both ways without a dependency cycle.
 * <p>
 * Replaces the successful {@code AllocateOrderResult} on {@code ALLOCATE_ORDER_RESPONSE_QUEUE}.
 *
 * @param orderId identifier of the allocated order
 */
public record OrderAllocated(Long orderId) {
}
