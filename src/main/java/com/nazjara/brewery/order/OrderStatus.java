package com.nazjara.brewery.order;

/**
 * Status of an order. Only {@code NEW} for now: validation happens synchronously before an order is stored, so the
 * old {@code VALIDATION_PENDING}, {@code VALIDATED} and {@code VALIDATION_EXCEPTION} states have nothing to represent.
 */
public enum OrderStatus {
    NEW
}
