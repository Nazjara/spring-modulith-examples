package com.nazjara.brewery.order.internal;

import com.nazjara.brewery.order.OrderNotCancellableException;
import com.nazjara.brewery.order.OrderStatus;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;

/**
 * Order aggregate, mapped to table {@code beer_order}; its lines live in {@code order_line} and are saved and loaded
 * with it, as Spring Data JDBC does for every collection inside an aggregate. Beers are referenced by UPC only.
 * <p>
 * Status changes return a new instance; the transitions are the ones listed on {@link OrderStatus}.
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

    /**
     * Moves the order from {@link OrderStatus#NEW} to {@link OrderStatus#ALLOCATED}.
     *
     * @return the allocated order
     * @throws IllegalStateException if the order is not {@code NEW}
     */
    public BeerOrder markAllocated() {
        return transition(OrderStatus.NEW, OrderStatus.ALLOCATED);
    }

    /**
     * Moves the order from {@link OrderStatus#NEW} to {@link OrderStatus#ALLOCATION_FAILED}.
     *
     * @return the order with failed allocation
     * @throws IllegalStateException if the order is not {@code NEW}
     */
    public BeerOrder markAllocationFailed() {
        return transition(OrderStatus.NEW, OrderStatus.ALLOCATION_FAILED);
    }

    /**
     * Cancels the order. Allowed only once allocation has finished ({@code ALLOCATED} or {@code ALLOCATION_FAILED}):
     * cancelling a {@code NEW} order could overtake the still-running allocation, which would then reserve stock for
     * a cancelled order.
     *
     * @return the cancelled order
     * @throws OrderNotCancellableException if the order is {@code NEW} or already {@code CANCELLED}
     */
    public BeerOrder cancel() {
        if (status != OrderStatus.ALLOCATED && status != OrderStatus.ALLOCATION_FAILED) {
            throw new OrderNotCancellableException(id, status);
        }
        return withStatus(OrderStatus.CANCELLED);
    }

    private BeerOrder transition(OrderStatus from, OrderStatus to) {
        if (status != from) {
            throw new IllegalStateException("Order %d is %s, expected %s".formatted(id, status, from));
        }
        return withStatus(to);
    }

    private BeerOrder withStatus(OrderStatus newStatus) {
        return new BeerOrder(id, customerRef, newStatus, lines);
    }
}
