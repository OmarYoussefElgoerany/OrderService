package com.screen.orders.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;

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
}
