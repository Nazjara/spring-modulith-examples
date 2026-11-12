package com.nazjara.brewery.order.internal;

import org.springframework.data.repository.ListCrudRepository;

/**
 * Spring Data JDBC repository for the {@link BeerOrder} aggregate.
 */
public interface BeerOrderRepository extends ListCrudRepository<BeerOrder, Long> {
}
