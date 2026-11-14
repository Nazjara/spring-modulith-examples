package com.nazjara.brewery.order;

import com.nazjara.brewery.catalog.CatalogService;
import com.nazjara.brewery.order.internal.BeerOrder;
import com.nazjara.brewery.order.internal.BeerOrderRepository;
import com.nazjara.brewery.order.internal.OrderLine;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order API: places, cancels and reads orders.
 * <p>
 * Placing an order validates every UPC by calling {@link CatalogService} directly, in the same thread and transaction,
 * before anything is stored. In the microservices version the state machine's {@code ValidateOrderAction} sent a
 * {@code ValidateOrderRequest} to {@code VALIDATE_ORDER_QUEUE}, {@code beer-service} answered on
 * {@code VALIDATE_ORDER_RESPONSE_QUEUE}, and the order waited in {@code VALIDATION_PENDING} until the reply arrived.
 * The call creates the dependency {@code order → catalog}, which {@code allowedDependencies} in this module's
 * {@code package-info.java} permits.
 * <p>
 * Allocation, by contrast, is asynchronous: {@link #place} and {@link #cancel} publish {@link OrderPlaced} and
 * {@link OrderCancelled} through Spring's {@link ApplicationEventPublisher}. The publisher does not know who listens,
 * so {@code order} gains no dependency on the allocating module. {@code @ApplicationModuleListener}s receive the
 * events only after the publishing transaction commits; if it rolls back, nobody sees them. This replaces the JMS
 * sends to {@code ALLOCATE_ORDER_QUEUE} and {@code DEALLOCATE_ORDER_QUEUE}.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final CatalogService catalog;
    private final BeerOrderRepository orders;
    private final ApplicationEventPublisher events;

    /**
     * Validates the ordered UPCs against the catalog, stores the order as {@link OrderStatus#NEW} and publishes
     * {@link OrderPlaced}. Allocation happens after this method's transaction commits, so the returned order is
     * always {@code NEW}.
     *
     * @param command the order to place
     * @return the stored order
     * @throws UnknownBeerException if any UPC is not in the catalog; nothing is stored or published
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
        var order = toInfo(orders.save(BeerOrder.place(command.customerRef(), lines)));
        events.publishEvent(new OrderPlaced(order.id(), order.lines()));
        return order;
    }

    /**
     * Cancels an order whose allocation has finished and publishes {@link OrderCancelled}, so any reserved stock is
     * released after this method's transaction commits.
     *
     * @param id order identifier
     * @return the cancelled order, or empty if none exists
     * @throws OrderNotCancellableException if the order is {@code NEW} (allocation still running) or already
     *                                      {@code CANCELLED}
     */
    @Transactional
    public Optional<OrderInfo> cancel(long id) {
        return orders.findById(id).map(order -> {
            var cancelled = orders.save(order.cancel());
            events.publishEvent(new OrderCancelled(cancelled.id()));
            return toInfo(cancelled);
        });
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
