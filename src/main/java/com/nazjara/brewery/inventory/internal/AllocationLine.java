package com.nazjara.brewery.inventory.internal;

/**
 * Quantity of one beer reserved by an {@link Allocation}, mapped to table {@code allocation_line}.
 *
 * @param upc      universal product code of the beer
 * @param quantity reserved units
 */
public record AllocationLine(String upc, int quantity) {
}
