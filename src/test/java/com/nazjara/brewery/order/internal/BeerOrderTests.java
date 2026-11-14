package com.nazjara.brewery.order.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nazjara.brewery.order.OrderNotCancellableException;
import com.nazjara.brewery.order.OrderStatus;

import java.util.List;

import org.junit.jupiter.api.Test;

class BeerOrderTests {

    private static final BeerOrder NEW_ORDER =
            new BeerOrder(1L, "tasting-room", OrderStatus.NEW, List.of(new OrderLine("0631234200036", 1)));

    @Test
    void newOrderCannotBeCancelledWhileAllocationIsRunning() {
        assertThatThrownBy(NEW_ORDER::cancel).isInstanceOf(OrderNotCancellableException.class);
    }

    @Test
    void allocatedOrderCanBeCancelled() {
        var cancelled = NEW_ORDER.markAllocated().cancel();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void orderWithFailedAllocationCanBeCancelled() {
        var cancelled = NEW_ORDER.markAllocationFailed().cancel();

        assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void allocationResultIsRejectedForOrderThatIsNoLongerNew() {
        var allocated = NEW_ORDER.markAllocated();

        assertThatThrownBy(allocated::markAllocationFailed).isInstanceOf(IllegalStateException.class);
    }
}
