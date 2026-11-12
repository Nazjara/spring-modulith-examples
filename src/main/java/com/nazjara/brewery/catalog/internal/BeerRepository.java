package com.nazjara.brewery.catalog.internal;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

/**
 * Spring Data JDBC repository for {@link Beer}. Must be {@code public} because {@code CatalogService} in the parent
 * package uses it, so Java visibility cannot hide it from other modules; {@code ApplicationModules.verify()} does.
 */
public interface BeerRepository extends ListCrudRepository<Beer, Long> {

    Optional<Beer> findByUpc(String upc);
}
