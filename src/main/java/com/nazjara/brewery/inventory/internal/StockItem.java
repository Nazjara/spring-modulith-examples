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
}
