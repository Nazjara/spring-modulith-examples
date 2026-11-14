package com.nazjara.brewery.inventory.internal;

import java.util.Optional;

import org.springframework.data.repository.ListCrudRepository;

/**
 * Spring Data JDBC repository for the {@link Allocation} aggregate.
 */
public interface AllocationRepository extends ListCrudRepository<Allocation, Long> {

    Optional<Allocation> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);
}
