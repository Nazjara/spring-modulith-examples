package com.nazjara.brewery.order.internal;

import java.util.Optional;

import org.springframework.data.relational.core.sql.LockMode;
import org.springframework.data.relational.repository.Lock;
import org.springframework.data.repository.ListCrudRepository;

/**
 * Spring Data JDBC repository for the {@link BeerOrder} aggregate.
 */
public interface BeerOrderRepository extends ListCrudRepository<BeerOrder, Long> {

    /**
     * Loads an order with {@code SELECT … FOR UPDATE} on its {@code beer_order} row. A concurrent writer of the same
     * order waits until this transaction ends and then reads the committed status, so a check-then-update on the
     * status cannot overwrite a change made in between.
     *
     * @param id order identifier
     * @return the order, locked until the transaction ends, or empty if none exists
     */
    @Lock(LockMode.PESSIMISTIC_WRITE)
    Optional<BeerOrder> findLockedById(Long id);
}
