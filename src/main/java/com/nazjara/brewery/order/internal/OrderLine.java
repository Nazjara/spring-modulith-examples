package com.nazjara.brewery.order.internal;

/**
 * One line of a {@link BeerOrder}, mapped to table {@code order_line}.
 *
 * @param upc      universal product code of the beer
 * @param quantity number of units
 */
public record OrderLine(String upc, int quantity) {
}
