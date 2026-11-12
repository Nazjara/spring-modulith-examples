package com.nazjara.brewery.order;

import com.nazjara.brewery.catalog.CatalogService;
import com.nazjara.brewery.order.internal.BeerOrder;
import com.nazjara.brewery.order.internal.BeerOrderRepository;
import com.nazjara.brewery.order.internal.OrderLine;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order API: places and reads orders.
 * <p>
 * Placing an order validates every UPC by calling {@link CatalogService} directly, in the same thread and transaction,
 * before anything is stored. In the microservices version the state machine's {@code ValidateOrderAction} sent a
 * {@code ValidateOrderRequest} to {@code VALIDATE_ORDER_QUEUE}, {@code beer-service} answered on
 * {@code VALIDATE_ORDER_RESPONSE_QUEUE}, and the order waited in {@code VALIDATION_PENDING} until the reply arrived.
 * The call creates the dependency {@code order → catalog}, which {@code allowedDependencies} in this module's
 * {@code package-info.java} permits.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CatalogService catalog;
    private final BeerOrderRepository orders;

    /**
     * Validates the ordered UPCs against the catalog and stores the order as {@link OrderStatus#NEW}.
     *
     * @param command the order to place
     * @return the stored order
     * @throws UnknownBeerException if any UPC is not in the catalog; nothing is stored
     */
    @Transactional
    public OrderInfo place(PlaceOrder command) {
        var unknownUpcs = command.lines().stream()
                .map(LineItem::upc)
                .distinct()
                .filter(upc -> catalog.findByUpc(upc).isEmpty())
                .toList();
        if (!unknownUpcs.isEmpty()) {
            throw new UnknownBeerException(unknownUpcs);
        }

        var lines = command.lines().stream()
                .map(line -> new OrderLine(line.upc(), line.quantity()))
                .toList();
        return toInfo(orders.save(BeerOrder.place(command.customerRef(), lines)));
    }

    /**
     * Finds an order by id.
     *
     * @param id order identifier
     * @return the order, or empty if none exists
     */
    @Transactional(readOnly = true)
    public Optional<OrderInfo> findById(long id) {
        return orders.findById(id).map(OrderService::toInfo);
    }

    private static OrderInfo toInfo(BeerOrder order) {
        var lines = order.lines().stream()
                .map(line -> new LineItem(line.upc(), line.quantity()))
                .toList();
        return new OrderInfo(order.id(), order.customerRef(), order.status(), lines);
    }
}
