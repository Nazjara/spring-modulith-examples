package com.nazjara.brewery.order.web;

import com.nazjara.brewery.order.OrderInfo;
import com.nazjara.brewery.order.OrderNotCancellableException;
import com.nazjara.brewery.order.OrderService;
import com.nazjara.brewery.order.PlaceOrder;
import com.nazjara.brewery.order.UnknownBeerException;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * REST endpoints of the order module.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/orders")
class OrderController {

    private final OrderService orders;

    /**
     * Places an order.
     *
     * @param command    the order to place
     * @param uriBuilder builder for the {@code Location} header
     * @return 201 with the stored order and its location
     */
    @PostMapping
    ResponseEntity<OrderInfo> place(@RequestBody PlaceOrder command, UriComponentsBuilder uriBuilder) {
        var order = orders.place(command);
        var location = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(order.id()).toUri();
        return ResponseEntity.created(location).body(order);
    }

    /**
     * Finds an order by id.
     *
     * @param id order identifier
     * @return 200 with the order, or 404 if none exists
     */
    @GetMapping("/{id}")
    ResponseEntity<OrderInfo> findById(@PathVariable long id) {
        return ResponseEntity.of(orders.findById(id));
    }

    /**
     * Cancels an order whose allocation has finished; reserved stock is released asynchronously.
     *
     * @param id order identifier
     * @return 200 with the cancelled order, or 404 if none exists
     */
    @PostMapping("/{id}/cancel")
    ResponseEntity<OrderInfo> cancel(@PathVariable long id) {
        return ResponseEntity.of(orders.cancel(id));
    }

    /**
     * Maps a cancellation in the wrong status to a 409 problem response.
     *
     * @param exception the rejected cancellation
     * @return the problem detail
     */
    @ExceptionHandler
    ProblemDetail handle(OrderNotCancellableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * Maps unknown UPCs to a 400 problem response that lists them.
     *
     * @param exception the validation failure
     * @return the problem detail with an {@code unknownUpcs} property
     */
    @ExceptionHandler
    ProblemDetail handle(UnknownBeerException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        problem.setProperty("unknownUpcs", exception.upcs());
        return problem;
    }
}
