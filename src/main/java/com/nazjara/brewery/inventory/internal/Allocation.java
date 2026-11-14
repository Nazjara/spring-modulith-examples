package com.nazjara.brewery.inventory.internal;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;

/**
 * Stock reserved for one order, mapped to table {@code allocation} with its lines in {@code allocation_line}. Refers
 * to the order by id only; the order itself belongs to another module.
 *
 * @param id      database identifier, assigned on insert
 * @param orderId identifier of the order the stock is reserved for
 * @param lines   reserved quantity per beer
 */
public record Allocation(
        @Id Long id,
        Long orderId,
        @MappedCollection(idColumn = "allocation_id", keyColumn = "line_no") List<AllocationLine> lines) {

    /**
     * Creates a new, unsaved allocation.
     *
     * @param orderId identifier of the order
     * @param lines   reserved quantity per beer
     * @return an unsaved allocation
     */
    public static Allocation of(Long orderId, List<AllocationLine> lines) {
        return new Allocation(null, orderId, List.copyOf(lines));
    }
}
