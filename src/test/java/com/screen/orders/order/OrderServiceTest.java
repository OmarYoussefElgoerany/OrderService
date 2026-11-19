package com.screen.orders.order;

import com.screen.orders.order.dto.OrderFilterRequest;
import com.screen.orders.order.dto.OrderItemRequest;
import com.screen.orders.order.dto.OrderRequest;
import com.screen.orders.order.dto.OrderResponse;
import com.screen.orders.order.dto.OrderStatusUpdateRequest;
import com.screen.orders.order.exception.EmptyOrderItemsException;
import com.screen.orders.order.exception.InvalidOrderStateTransitionException;
import com.screen.orders.order.exception.OrderAccessDeniedException;
import com.screen.orders.order.exception.OrderModificationNotAllowedException;
import com.screen.orders.order.exception.OrderNotFoundException;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  private OrderService orderService;
  private User regularUser;
  private User otherUser;
  private User adminUser;
  private User managerUser;

  @BeforeEach
  void setUp() {
    orderService = new OrderService(orderRepository, Mappers.getMapper(OrderMapper.class));
    regularUser = User.builder().id(1).email("user@example.com").role(Role.USER).build();
    otherUser = User.builder().id(2).email("other@example.com").role(Role.USER).build();
    adminUser = User.builder().id(10).email("admin@example.com").role(Role.ADMIN).build();
    managerUser = User.builder().id(11).email("manager@example.com").role(Role.MANAGEMENT).build();
  }

  private static Principal authFor(User user) {
    return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
  }

  @Test
  void createOrderAssignsAuthenticatedOwnerAndCalculatesServerSideTotals() {
    OrderRequest request = new OrderRequest(
        "123 Main St, Springfield",
        "Leave at front door",
        "eur",
        List.of(
            new OrderItemRequest("PROD-1", "Mechanical Keyboard", 2, new BigDecimal("79.50")),
            new OrderItemRequest("PROD-2", "USB-C Cable", 3, new BigDecimal("10.00"))
        )
    );

    when(orderRepository.existsByOrderNumber(any())).thenReturn(false);
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
      Order o = inv.getArgument(0);
      o.setId(100);
      return o;
    });

    OrderResponse response = orderService.createOrder(request, authFor(regularUser));

    assertEquals(100, response.id());
    assertEquals(1, response.userId());
    assertEquals("user@example.com", response.userEmail());
    assertEquals(OrderStatus.PENDING, response.status());
    assertEquals("EUR", response.currency());
    assertEquals(new BigDecimal("189.00"), response.totalAmount());
    assertTrue(response.orderNumber().startsWith("ORD-"));
    assertEquals(2, response.items().size());
    assertEquals(new BigDecimal("159.00"), response.items().get(0).subtotal());
    assertEquals(new BigDecimal("30.00"), response.items().get(1).subtotal());
  }

  @Test
  void createOrderWithEmptyItemsThrowsEmptyOrderItemsException() {
    OrderRequest request = new OrderRequest("123 Main St", null, "USD", List.of());

    assertThrows(
        EmptyOrderItemsException.class,
        () -> orderService.createOrder(request, authFor(regularUser))
    );
    verify(orderRepository, never()).save(any());
  }

  @Test
  void roleUserCanViewOwnOrderButNotAnotherUsersOrder() {
    Order otherUsersOrder = Order.builder()
        .id(55)
        .user(otherUser)
        .orderNumber("ORD-2026-ABCDEF12")
        .status(OrderStatus.PENDING)
        .shippingAddress("456 Oak Ave")
        .items(new ArrayList<>())
        .build();

    when(orderRepository.findWithDetailsById(55)).thenReturn(Optional.of(otherUsersOrder));

    assertThrows(
        OrderAccessDeniedException.class,
        () -> orderService.findOrderById(55, authFor(regularUser))
    );
  }

  @Test
  void roleUserUpdateAndCancelOnAnotherUsersOrderThrowsAccessDenied() {
    Order otherUsersOrder = Order.builder()
        .id(56)
        .user(otherUser)
        .orderNumber("ORD-2026-ABCDEF34")
        .status(OrderStatus.PENDING)
        .shippingAddress("456 Oak Ave")
        .items(new ArrayList<>())
        .build();

    when(orderRepository.findWithDetailsById(56)).thenReturn(Optional.of(otherUsersOrder));

    OrderRequest updateRequest = new OrderRequest(
        "999 New St",
        null,
        "USD",
        List.of(new OrderItemRequest("P1", "Item", 1, new BigDecimal("25.00")))
    );

    assertThrows(
        OrderAccessDeniedException.class,
        () -> orderService.updateOrder(56, updateRequest, authFor(regularUser))
    );
    assertThrows(
        OrderAccessDeniedException.class,
        () -> orderService.cancelOrder(56, "Changed mind", authFor(regularUser))
    );
    verify(orderRepository, never()).save(any());
  }

  @Test
  void roleAdminAndManagementCanViewAndTransitionOrdersAcrossUsers() {
    Order order = Order.builder()
        .id(77)
        .user(regularUser)
        .orderNumber("ORD-2026-11223344")
        .status(OrderStatus.PENDING)
        .shippingAddress("789 Pine Rd")
        .items(new ArrayList<>())
        .build();

    when(orderRepository.findWithDetailsById(77)).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    OrderResponse fetched = orderService.findOrderById(77, authFor(managerUser));
    assertEquals(77, fetched.id());

    OrderResponse confirmed = orderService.updateOrderStatus(
        77,
        new OrderStatusUpdateRequest(OrderStatus.CONFIRMED),
        authFor(managerUser)
    );
    assertEquals(OrderStatus.CONFIRMED, confirmed.status());

    OrderResponse processing = orderService.updateOrderStatus(
        77,
        new OrderStatusUpdateRequest(OrderStatus.PROCESSING),
        authFor(adminUser)
    );
    assertEquals(OrderStatus.PROCESSING, processing.status());
  }

  @Test
  void findAllOrdersCapsPageSizeAt100AndMapsCreatedDateSortAlias() {
    Order order = Order.builder()
        .id(1)
        .user(regularUser)
        .orderNumber("ORD-2026-00000001")
        .status(OrderStatus.PENDING)
        .shippingAddress("100 First St")
        .items(new ArrayList<>())
        .build();

    Pageable overscaled = PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "createdDate"));
    when(orderRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(order)));

    Page<OrderResponse> page = orderService.findAllOrders(
        OrderFilterRequest.byStatus(OrderStatus.PENDING), overscaled, authFor(regularUser)
    );

    assertEquals(1, page.getTotalElements());
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(orderRepository).findAll(any(Specification.class), pageableCaptor.capture());
    assertEquals(100, pageableCaptor.getValue().getPageSize());
    assertEquals("createDate", pageableCaptor.getValue().getSort().iterator().next().getProperty());
  }

  @Test
  void editingShippedOrderThrowsOrderModificationNotAllowedException() {
    Order shippedOrder = Order.builder()
        .id(88)
        .user(regularUser)
        .orderNumber("ORD-2026-SHIP0088")
        .status(OrderStatus.SHIPPED)
        .shippingAddress("789 Pine Rd")
        .items(new ArrayList<>())
        .build();

    when(orderRepository.findWithDetailsById(88)).thenReturn(Optional.of(shippedOrder));

    OrderRequest updateRequest = new OrderRequest(
        "New Address",
        "Updated note",
        "USD",
        List.of(new OrderItemRequest("P1", "Widget", 1, new BigDecimal("19.99")))
    );

    OrderModificationNotAllowedException ex = assertThrows(
        OrderModificationNotAllowedException.class,
        () -> orderService.updateOrder(88, updateRequest, authFor(regularUser))
    );

    assertEquals(OrderStatus.SHIPPED, ex.getCurrentStatus());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void illegalStateTransitionsThrowInvalidOrderStateTransitionException() {
    Order deliveredOrder = Order.builder()
        .id(90)
        .user(regularUser)
        .orderNumber("ORD-2026-DELV0090")
        .status(OrderStatus.DELIVERED)
        .shippingAddress("789 Pine Rd")
        .items(new ArrayList<>())
        .build();

    when(orderRepository.findWithDetailsById(90)).thenReturn(Optional.of(deliveredOrder));

    InvalidOrderStateTransitionException ex = assertThrows(
        InvalidOrderStateTransitionException.class,
        () -> orderService.cancelOrder(90, "Too late", authFor(adminUser))
    );

    assertEquals(OrderStatus.DELIVERED, ex.getCurrentStatus());
    assertEquals(OrderStatus.CANCELLED, ex.getTargetStatus());

    Order pendingOrder = Order.builder()
        .id(91)
        .user(regularUser)
        .orderNumber("ORD-2026-PEND0091")
        .status(OrderStatus.PENDING)
        .shippingAddress("789 Pine Rd")
        .items(new ArrayList<>())
        .build();
    when(orderRepository.findWithDetailsById(91)).thenReturn(Optional.of(pendingOrder));

    InvalidOrderStateTransitionException skipEx = assertThrows(
        InvalidOrderStateTransitionException.class,
        () -> orderService.updateOrderStatus(91, new OrderStatusUpdateRequest(OrderStatus.DELIVERED), authFor(adminUser))
    );
    assertEquals(OrderStatus.PENDING, skipEx.getCurrentStatus());
    assertEquals(OrderStatus.DELIVERED, skipEx.getTargetStatus());
  }

  @Test
  void roleUserCannotCancelShippedOrderWhileAdminCan() {
    Order shippedForUser = Order.builder()
        .id(92)
        .user(regularUser)
        .orderNumber("ORD-2026-SHIP0092")
        .status(OrderStatus.SHIPPED)
        .shippingAddress("789 Pine Rd")
        .items(new ArrayList<>())
        .build();

    when(orderRepository.findWithDetailsById(92)).thenReturn(Optional.of(shippedForUser));

    assertThrows(
        InvalidOrderStateTransitionException.class,
        () -> orderService.cancelOrder(92, "User cancel", authFor(regularUser))
    );

    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    OrderResponse adminCancelled = orderService.cancelOrder(92, "Lost in transit", authFor(adminUser));
    assertEquals(OrderStatus.CANCELLED, adminCancelled.status());
    assertEquals("Lost in transit", adminCancelled.cancellationReason());
    assertNotNull(adminCancelled.cancelledAt());
  }

  @Test
  void missingOrderThrowsOrderNotFoundException() {
    when(orderRepository.findWithDetailsById(404)).thenReturn(Optional.empty());

    assertThrows(
        OrderNotFoundException.class,
        () -> orderService.findOrderById(404, authFor(adminUser))
    );
  }
}
