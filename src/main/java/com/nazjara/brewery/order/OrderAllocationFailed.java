package com.nazjara.brewery.order;

/**
 * Reply to {@link OrderPlaced}: the order could not be allocated in full, and nothing was reserved. Owned by
 * {@code order} for the same reason as {@link OrderAllocated}.
 * <p>
 * Replaces the failed {@code AllocateOrderResult} on {@code ALLOCATE_ORDER_RESPONSE_QUEUE}.
 *
 * @param orderId identifier of the order
 */
public record OrderAllocationFailed(Long orderId) {
}
