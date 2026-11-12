package com.nazjara.brewery.order;

/**
 * One line of an order: a beer, referenced by UPC, and a quantity.
 *
 * @param upc      universal product code of the beer
 * @param quantity number of units, at least 1
 */
public record LineItem(String upc, int quantity) {

    public LineItem {
        if (upc == null || upc.isBlank()) {
            throw new IllegalArgumentException("upc must not be blank");
        }
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
    }
}
