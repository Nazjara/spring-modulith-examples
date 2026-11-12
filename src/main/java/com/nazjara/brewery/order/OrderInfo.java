package com.nazjara.brewery.order;

import java.util.List;

/**
 * An order as other modules and the HTTP layer see it. The persistent {@code BeerOrder} aggregate stays in
 * {@code order.internal}.
 *
 * @param id          order identifier
 * @param customerRef customer reference
 * @param status      current status
 * @param lines       ordered beers
 */
public record OrderInfo(Long id, String customerRef, OrderStatus status, List<LineItem> lines) {
}
