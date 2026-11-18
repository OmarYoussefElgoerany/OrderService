package com.screen.orders.order;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;

    public void save(OrderRequest request) {
        var book = Order.builder()
                .id(request.getId())
                .name(request.getName())
                .build();
        repository.save(book);
    }

    public List<Order> findAll() {
        return repository.findAll();
    }
}
