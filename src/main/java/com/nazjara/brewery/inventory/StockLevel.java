package com.nazjara.brewery.inventory;

/**
 * Stock of one beer as other modules see it. Part of the inventory API; the persistent {@code StockItem} stays in
 * {@code inventory.internal}.
 *
 * @param upc            universal product code of the beer
 * @param quantityOnHand units in stock
 */
public record StockLevel(String upc, int quantityOnHand) {
}
