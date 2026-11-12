package com.nazjara.brewery.order;

import java.util.List;

/**
 * Command to place an order. Part of the order API, used by the HTTP layer and by any module allowed to place orders.
 *
 * @param customerRef caller-supplied customer reference
 * @param lines       ordered beers, at least one
 */
public record PlaceOrder(String customerRef, List<LineItem> lines) {

    public PlaceOrder {
        if (customerRef == null || customerRef.isBlank()) {
            throw new IllegalArgumentException("customerRef must not be blank");
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("an order needs at least one line");
        }
        lines = List.copyOf(lines);
    }
}
