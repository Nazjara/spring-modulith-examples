package com.nazjara.brewery.inventory.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.ListCrudRepository;

/**
 * Spring Data JDBC repository for {@link StockItem}.
 */
public interface StockItemRepository extends ListCrudRepository<StockItem, Long> {

    Optional<StockItem> findByUpc(String upc);

    /**
     * Loads the stock of the given beers with {@code SELECT … FOR UPDATE}, in UPC order. The row locks make the
     * check-then-update of an allocation safe against concurrent allocations of the same beer; the fixed order keeps
     * two allocations from locking the same rows in opposite order and deadlocking.
     *
     * @param upcs universal product codes
     * @return the stock items found, locked until the transaction ends
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    List<StockItem> findByUpcInOrderByUpc(Collection<String> upcs);
}
