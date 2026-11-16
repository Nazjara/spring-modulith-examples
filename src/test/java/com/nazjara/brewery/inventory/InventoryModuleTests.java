package com.nazjara.brewery.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.nazjara.brewery.TestcontainersConfiguration;
import com.nazjara.brewery.order.LineItem;
import com.nazjara.brewery.order.OrderAllocated;
import com.nazjara.brewery.order.OrderAllocationFailed;
import com.nazjara.brewery.order.OrderPlaced;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.modulith.test.Scenario;

/**
 * Tests the {@code inventory} module on its own: {@code @ApplicationModuleTest} in its default {@code STANDALONE} mode
 * bootstraps only this module's beans, on a real database and with the real after-commit, async listener dispatch.
 * {@code inventory} depends on {@code order} through event types only, and Modulith counts only injected beans as
 * bootstrap dependencies, so {@code order} is absent in every mode and nothing is mocked. The test plays
 * {@code order}'s side of the conversation: it publishes {@code OrderPlaced} and checks the reply. In the microservices
 * version this was a JMS test harness putting a message on {@code ALLOCATE_ORDER_QUEUE} and reading the response
 * queue.
 * <p>
 * The listener publishes its reply inside its own transaction, and the test's event capture records it at
 * {@code publishEvent} time, before that transaction commits. A test that reads the stock right after the reply
 * arrives can therefore see the old value; waiting for the stock change first and then expecting the event avoids
 * that race.
 */
@ApplicationModuleTest
@Import(TestcontainersConfiguration.class)
@RequiredArgsConstructor
class InventoryModuleTests {

    private static final String BLESSED_UPC = "0631234200038";
    private static final String CACTUS_GOSE_UPC = "0631234200039";
    private static final String NO_HAMMERS_UPC = "0631234200037";

    private final InventoryService inventory;

    @Test
    void allocatesOrderWithEnoughStockAndRepliesAllocated(Scenario scenario) {
        var stockBefore = stockOf(BLESSED_UPC);

        scenario.publish(new OrderPlaced(9001L, List.of(new LineItem(BLESSED_UPC, 3))))
                .andWaitForStateChange(() -> stockOf(BLESSED_UPC), stock -> stock == stockBefore - 3)
                .andExpect(OrderAllocated.class)
                .matchingMappedValue(OrderAllocated::orderId, 9001L)
                .toArrive();
    }

    @Test
    void rejectsOrderExceedingStockAndReservesNothing(Scenario scenario) {
        var goseBefore = stockOf(CACTUS_GOSE_UPC);
        var lines = List.of(new LineItem(CACTUS_GOSE_UPC, 1), new LineItem(NO_HAMMERS_UPC, 1_000));

        scenario.publish(new OrderPlaced(9002L, lines))
                .andWaitForEventOfType(OrderAllocationFailed.class)
                .matchingMappedValue(OrderAllocationFailed::orderId, 9002L)
                .toArrive();

        assertThat(stockOf(CACTUS_GOSE_UPC)).isEqualTo(goseBefore);
    }

    private int stockOf(String upc) {
        return inventory.stockOf(upc).orElseThrow().quantityOnHand();
    }
}
