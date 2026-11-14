package com.nazjara.brewery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.nazjara.brewery.inventory.InventoryService;
import com.nazjara.brewery.order.LineItem;
import com.nazjara.brewery.order.OrderInfo;
import com.nazjara.brewery.order.OrderService;
import com.nazjara.brewery.order.OrderStatus;
import com.nazjara.brewery.order.PlaceOrder;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BreweryIntegrationTests {

    private static final String MANGO_BOBS_UPC = "0631234200036";
    private static final String GALAXY_CAT_UPC = "0631234300019";
    private static final String PINBALL_PORTER_UPC = "0083783375213";
    private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private OrderService orders;

    @Autowired
    private InventoryService inventory;

    @Test
    void catalogListsMigratedBeers() {
        var response = mvc.get().uri("/api/v1/beers");

        assertThat(response).hasStatusOk().bodyJson().extractingPath("$.length()").isEqualTo(6);
    }

    @Test
    void inventoryReturnsMigratedStock() {
        var response = mvc.get().uri("/api/v1/inventory/{upc}", "0631234200037");

        assertThat(response).hasStatusOk().bodyJson().extractingPath("$.quantityOnHand").isEqualTo(50);
    }

    @Test
    void placedOrderIsReturnedAsNewThenAllocatedAsynchronously() {
        var stockBefore = stockOf(MANGO_BOBS_UPC);
        var body = """
                {"customerRef": "tasting-room", "lines": [{"upc": "%s", "quantity": 2}, {"upc": "%s", "quantity": 1}]}
                """.formatted(MANGO_BOBS_UPC, MANGO_BOBS_UPC);

        var placed = mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body).exchange();

        assertThat(placed).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.status").isEqualTo("NEW");
        var location = placed.getResponse().getHeader(HttpHeaders.LOCATION);
        awaitStatus(Long.parseLong(location.substring(location.lastIndexOf('/') + 1)), OrderStatus.ALLOCATED);
        assertThat(stockOf(MANGO_BOBS_UPC)).isEqualTo(stockBefore - 3);
    }

    @Test
    void cancellingAllocatedOrderReleasesItsStock() {
        var stockBefore = stockOf(GALAXY_CAT_UPC);
        var order = orders.place(new PlaceOrder("tasting-room", List.of(new LineItem(GALAXY_CAT_UPC, 4))));
        awaitStatus(order.id(), OrderStatus.ALLOCATED);

        var cancelled = mvc.post().uri("/api/v1/orders/{id}/cancel", order.id());

        assertThat(cancelled).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() -> assertThat(stockOf(GALAXY_CAT_UPC)).isEqualTo(stockBefore));
    }

    @Test
    void orderExceedingStockFailsAllocationAndReservesNothing() {
        var porterBefore = stockOf(PINBALL_PORTER_UPC);
        var lines = List.of(new LineItem(PINBALL_PORTER_UPC, 1), new LineItem(MANGO_BOBS_UPC, 1_000));

        var order = orders.place(new PlaceOrder("tasting-room", lines));

        awaitStatus(order.id(), OrderStatus.ALLOCATION_FAILED);
        assertThat(stockOf(PINBALL_PORTER_UPC)).isEqualTo(porterBefore);
    }

    @Test
    void cancellingCancelledOrderIsAConflict() {
        var order = orders.place(new PlaceOrder("tasting-room", List.of(new LineItem(MANGO_BOBS_UPC, 1_000))));
        awaitStatus(order.id(), OrderStatus.ALLOCATION_FAILED);
        orders.cancel(order.id());

        var response = mvc.post().uri("/api/v1/orders/{id}/cancel", order.id());

        assertThat(response).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void cancellingUnknownOrderIsNotFound() {
        var response = mvc.post().uri("/api/v1/orders/{id}/cancel", Long.MAX_VALUE);

        assertThat(response).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void placingOrderWithUnknownBeerIsRejectedWithTheUnknownUpcs() {
        var body = """
                {"customerRef": "tasting-room", "lines": [{"upc": "%s", "quantity": 1}, {"upc": "nope", "quantity": 1}]}
                """.formatted(MANGO_BOBS_UPC);

        var response = mvc.post().uri("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(body);

        assertThat(response).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.unknownUpcs").asArray().containsExactly("nope");
    }

    private int stockOf(String upc) {
        return inventory.stockOf(upc).orElseThrow().quantityOnHand();
    }

    private void awaitStatus(long orderId, OrderStatus status) {
        await().atMost(ASYNC_TIMEOUT).untilAsserted(() ->
                assertThat(orders.findById(orderId)).map(OrderInfo::status).contains(status));
    }
}
