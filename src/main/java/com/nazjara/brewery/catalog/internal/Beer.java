package com.nazjara.brewery.catalog.internal;

import com.nazjara.brewery.catalog.BeerStyle;

import java.math.BigDecimal;

import org.springframework.data.annotation.Id;

/**
 * Beer aggregate, mapped to table {@code beer} by Spring Data JDBC. Internal to the catalog module: other modules see
 * beers only as {@code BeerInfo} and reference them by {@code upc}.
 *
 * @param id        database identifier
 * @param upc       universal product code
 * @param beerName  display name
 * @param beerStyle style
 * @param price     unit price
 */
public record Beer(@Id Long id, String upc, String beerName, BeerStyle beerStyle, BigDecimal price) {
}
