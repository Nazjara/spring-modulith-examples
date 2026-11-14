package com.nazjara.brewery.order;

/**
 * Status of an order: {@code NEW → ALLOCATED | ALLOCATION_FAILED}, and from either of those {@code → CANCELLED}.
 * <p>
 * Validation happens synchronously before an order is stored, so the old {@code VALIDATION_PENDING},
 * {@code VALIDATED} and {@code VALIDATION_EXCEPTION} states have nothing to represent. {@code NEW} plays the role of
 * the old {@code ALLOCATION_PENDING}. Allocation is all-or-nothing, so there is no {@code PENDING_INVENTORY}.
 */
public enum OrderStatus {
    NEW,
    ALLOCATED,
    ALLOCATION_FAILED,
    CANCELLED
}
