package com.nazjara.brewery.order.internal;

import com.nazjara.brewery.order.OrderStatus;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;

/**
 * Order aggregate, mapped to table {@code beer_order}; its lines live in {@code order_line} and are saved and loaded
 * with it, as Spring Data JDBC does for every collection inside an aggregate. Beers are referenced by UPC only.
 *
 * @param id          database identifier, assigned on insert
 * @param customerRef customer reference
 * @param status      current status
 * @param lines       ordered beers
 */
public record BeerOrder(
        @Id Long id,
        String customerRef,
        OrderStatus status,
        @MappedCollection(idColumn = "beer_order_id", keyColumn = "line_no") List<OrderLine> lines) {

    /**
     * Creates a new, unsaved order in status {@link OrderStatus#NEW}.
     *
     * @param customerRef customer reference
     * @param lines       ordered beers
     * @return an unsaved order
     */
    public static BeerOrder place(String customerRef, List<OrderLine> lines) {
        return new BeerOrder(null, customerRef, OrderStatus.NEW, List.copyOf(lines));
    }
}
