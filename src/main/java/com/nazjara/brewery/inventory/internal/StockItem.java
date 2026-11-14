package com.nazjara.brewery.inventory.internal;

import org.springframework.data.annotation.Id;

/**
 * Stock aggregate, mapped to table {@code stock_item}. References the beer by {@code upc}.
 *
 * @param id             database identifier
 * @param upc            universal product code of the beer
 * @param quantityOnHand units in stock
 */
public record StockItem(@Id Long id, String upc, int quantityOnHand) {

    /**
     * Returns this item with {@code quantity} units taken out of stock.
     *
     * @param quantity units to take
     * @return the reduced item
     * @throws IllegalArgumentException if fewer than {@code quantity} units are on hand
     */
    public StockItem take(int quantity) {
        if (quantity > quantityOnHand) {
            throw new IllegalArgumentException("Only %d of %s on hand, %d requested".formatted(quantityOnHand, upc, quantity));
        }
        return new StockItem(id, upc, quantityOnHand - quantity);
    }

    /**
     * Returns this item with {@code quantity} units put back into stock.
     *
     * @param quantity units to put back
     * @return the increased item
     */
    public StockItem putBack(int quantity) {
        return new StockItem(id, upc, quantityOnHand + quantity);
    }
}
