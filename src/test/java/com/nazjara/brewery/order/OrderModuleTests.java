package com.nazjara.brewery.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;

import com.nazjara.brewery.EventPublicationRows;
import com.nazjara.brewery.EventPublicationRows.Row;
import com.nazjara.brewery.TestcontainersConfiguration;
import com.nazjara.brewery.order.internal.BeerOrder;
import com.nazjara.brewery.order.internal.BeerOrderRepository;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.modulith.test.Scenario;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Tests the {@code order} module with its direct dependencies: {@code DIRECT_DEPENDENCIES} bootstraps {@code order}
 * plus {@code catalog}, because {@code OrderService} injects {@code CatalogService}. {@code catalog} is cheap and
 * deterministic, so it runs for real instead of being mocked. {@code inventory} only listens to {@code order}'s events,
 * so it is not a dependency and stays out: nobody answers {@code OrderPlaced}, and the test publishes
 * {@code inventory}'s replies itself. In the microservices version this was a {@code beer-order-service} test with a
 * JMS harness standing in for {@code beer-inventory-service}.
 * <p>
 * The Event Publication Registry tests run the real resubmission schedule ({@code EventPublicationMaintenance}, a
 * root-package bean and therefore part of every module test), shortened to a fraction of a second. A failure is
 * simulated by making the repository spy throw once, the way a dropped database connection would.
 */
@ApplicationModuleTest(BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {
        "brewery.events.resubmission.interval=PT0.2S",
        "brewery.events.resubmission.min-age=PT0S"})
@RequiredArgsConstructor
class OrderModuleTests {

    private static final String MANGO_BOBS_UPC = "0631234200036";

    private final OrderService orders;
    private final JdbcTemplate jdbc;

    @MockitoSpyBean
    private BeerOrderRepository repository;

    @Test
    void placingOrderPublishesOrderPlacedWithItsLines(AssertablePublishedEvents events) {
        var order = place();

        assertThat(events).contains(OrderPlaced.class)
                .matchingValue(OrderPlaced::orderId, order.id())
                .matchingValue(OrderPlaced::lines, List.of(new LineItem(MANGO_BOBS_UPC, 2)));
    }

    @Test
    void allocatedReplyMarksOrderAllocated(Scenario scenario) {
        var order = place();

        scenario.publish(new OrderAllocated(order.id()))
                .andWaitForStateChange(() -> statusOf(order.id()), OrderStatus.ALLOCATED::equals);
    }

    @Test
    void allocationFailedReplyMarksOrderAllocationFailed(Scenario scenario) {
        var order = place();

        scenario.publish(new OrderAllocationFailed(order.id()))
                .andWaitForStateChange(() -> statusOf(order.id()), OrderStatus.ALLOCATION_FAILED::equals);
    }

    @Test
    void failedReplyIsResubmittedUntilItCompletes(Scenario scenario) {
        var order = place();
        var delegateToRepository = mockingDetails(repository).getMockCreationSettings().getDefaultAnswer();
        doThrow(new TransientDataAccessResourceException("Connection reset"))
                .doAnswer(delegateToRepository)
                .when(repository).save(argThat((BeerOrder saved) -> order.id().equals(saved.id())));

        scenario.publish(new OrderAllocated(order.id()))
                .andWaitForStateChange(() -> EventPublicationRows.of(jdbc, OrderAllocated.class, order.id()),
                        rows -> rows.equals(List.of(new Row("COMPLETED", 2))));

        assertThat(statusOf(order.id())).isEqualTo(OrderStatus.ALLOCATED);
    }

    @Test
    void repeatedReplyIsIgnored(Scenario scenario) {
        var order = place();
        scenario.publish(new OrderAllocated(order.id()))
                .andWaitForStateChange(() -> EventPublicationRows.of(jdbc, OrderAllocated.class, order.id()),
                        rows -> rows.equals(List.of(Row.COMPLETED_FIRST_TIME)));

        scenario.publish(new OrderAllocated(order.id()))
                .andWaitForStateChange(() -> EventPublicationRows.of(jdbc, OrderAllocated.class, order.id()),
                        rows -> rows.equals(List.of(Row.COMPLETED_FIRST_TIME, Row.COMPLETED_FIRST_TIME)));

        assertThat(statusOf(order.id())).isEqualTo(OrderStatus.ALLOCATED);
    }

    private OrderInfo place() {
        return orders.place(new PlaceOrder("tasting-room", List.of(new LineItem(MANGO_BOBS_UPC, 2))));
    }

    private OrderStatus statusOf(long orderId) {
        return orders.findById(orderId).map(OrderInfo::status).orElseThrow();
    }
}
