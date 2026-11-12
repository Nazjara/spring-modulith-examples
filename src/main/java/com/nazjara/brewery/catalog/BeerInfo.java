package com.nazjara.brewery.catalog;

import java.math.BigDecimal;

/**
 * A beer as other modules see it. Lives in the module's root package, so it is part of the catalog API; the
 * persistent {@code Beer} aggregate stays in {@code catalog.internal}. Returning the aggregate itself would leak an
 * internal type, and {@code ApplicationModules.verify()} would reject every caller that touches it.
 * <p>
 * Replaces the {@code BeerDto} that {@code beer-service} returned over REST and JMS.
 *
 * @param upc       universal product code, the identifier other modules use to reference a beer
 * @param beerName  display name
 * @param beerStyle style
 * @param price     unit price
 */
public record BeerInfo(String upc, String beerName, BeerStyle beerStyle, BigDecimal price) {
}
