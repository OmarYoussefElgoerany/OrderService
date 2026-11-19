package com.screen.orders.order;

import com.screen.orders.order.dto.OrderRequest;
import com.screen.orders.order.dto.OrderResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

  private final OrderRepository repository;
  private final OrderMapper orderMapper;

  public void save(OrderRequest request) {
    repository.save(orderMapper.toEntity(request));
  }

  public List<OrderResponse> findAll() {
    return orderMapper.toResponses(repository.findAll());
  }
}
