package com.screen.orders.order;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @PostMapping
    public ResponseEntity<?> save(
            @RequestBody OrderRequest request
    ) {
        service.save(request);
        return ResponseEntity.accepted().build();
    }

    @GetMapping
    public ResponseEntity<List<Order>> findAllOrders() {
        return ResponseEntity.ok(service.findAll());
    }
}
