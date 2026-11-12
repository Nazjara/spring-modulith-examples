package com.nazjara.brewery.inventory.internal;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

/**
 * Spring Data JDBC repository for {@link StockItem}.
 */
public interface StockItemRepository extends ListCrudRepository<StockItem, Long> {

    Optional<StockItem> findByUpc(String upc);
}
