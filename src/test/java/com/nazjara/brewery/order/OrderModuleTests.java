package com.nazjara.brewery.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.brewery.TestcontainersConfiguration;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.ApplicationModuleTest.BootstrapMode;
import org.springframework.modulith.test.AssertablePublishedEvents;
import org.springframework.modulith.test.Scenario;

/**
 * Tests the {@code order} module with its direct dependencies: {@code DIRECT_DEPENDENCIES} bootstraps {@code order}
 * plus {@code catalog}, because {@code OrderService} injects {@code CatalogService}. {@code catalog} is cheap and
 * deterministic, so it runs for real instead of being mocked. {@code inventory} only listens to {@code order}'s events,
 * so it is not a dependency and stays out: nobody answers {@code OrderPlaced}, and the test publishes
 * {@code inventory}'s replies itself. In the microservices version this was a {@code beer-order-service} test with a
 * JMS harness standing in for {@code beer-inventory-service}.
 */
@ApplicationModuleTest(BootstrapMode.DIRECT_DEPENDENCIES)
@Import(TestcontainersConfiguration.class)
@RequiredArgsConstructor
class OrderModuleTests {

    private static final String MANGO_BOBS_UPC = "0631234200036";

    private final OrderService orders;

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

    private OrderInfo place() {
        return orders.place(new PlaceOrder("tasting-room", List.of(new LineItem(MANGO_BOBS_UPC, 2))));
    }

    private OrderStatus statusOf(long orderId) {
        return orders.findById(orderId).map(OrderInfo::status).orElseThrow();
    }
}
