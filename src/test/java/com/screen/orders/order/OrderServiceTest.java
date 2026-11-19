package com.screen.orders.order;

import com.screen.orders.order.dto.OrderRequest;
import com.screen.orders.order.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService = new OrderService(orderRepository, Mappers.getMapper(OrderMapper.class));
  }

  @Test
  void saveMapsOrderRequestAndLeavesJpaAuditingFieldsNull() {
    OrderRequest request = OrderRequest.builder()
        .id(42)
        .name("Sample Order")
        .build();

    orderService.save(request);

    ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
    verify(orderRepository).save(captor.capture());
    Order saved = captor.getValue();
    assertEquals(42, saved.getId());
    assertEquals("Sample Order", saved.getName());
    assertNull(saved.getCreateDate());
    assertNull(saved.getLastModified());
    assertNull(saved.getCreatedBy());
    assertNull(saved.getLastModifiedBy());
  }

  @Test
  void findAllMapsOrdersToOrderResponses() {
    LocalDateTime now = LocalDateTime.now();
    Order order = Order.builder()
        .id(7)
        .name("Widget")
        .createDate(now)
        .createdBy(1)
        .build();
    when(orderRepository.findAll()).thenReturn(List.of(order));

    List<OrderResponse> responses = orderService.findAll();

    assertEquals(1, responses.size());
    assertEquals(7, responses.get(0).id());
    assertEquals("Widget", responses.get(0).name());
    assertEquals(now, responses.get(0).createDate());
    assertEquals(1, responses.get(0).createdBy());
  }
}
