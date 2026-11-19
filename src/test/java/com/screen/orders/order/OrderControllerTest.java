package com.screen.orders.order;

import com.screen.orders.common.exception.GlobalExceptionHandler;
import com.screen.orders.common.security.JwtService;
import com.screen.orders.common.security.SecurityConfiguration;
import com.screen.orders.order.dto.OrderFilterRequest;
import com.screen.orders.order.dto.OrderItemResponse;
import com.screen.orders.order.dto.OrderResponse;
import com.screen.orders.order.exception.InvalidOrderStateTransitionException;
import com.screen.orders.order.exception.OrderAccessDeniedException;
import com.screen.orders.order.exception.OrderModificationNotAllowedException;
import com.screen.orders.order.exception.OrderNotFoundException;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@ActiveProfiles("test")
@Import({SecurityConfiguration.class, GlobalExceptionHandler.class})
class OrderControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private OrderService orderService;

  @MockitoBean
  private JwtService jwtService;

  @MockitoBean
  private UserDetailsService userDetailsService;

  @MockitoBean
  private AuthenticationProvider authenticationProvider;

  private UsernamePasswordAuthenticationToken userPrincipal;
  private UsernamePasswordAuthenticationToken adminPrincipal;

  @BeforeEach
  void setUp() {
    User user = User.builder().id(1).email("user@example.com").role(Role.USER).build();
    User admin = User.builder().id(10).email("admin@example.com").role(Role.ADMIN).build();
    userPrincipal = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    adminPrincipal = new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities());
  }

  private static OrderResponse sampleResponse(Integer id, OrderStatus status) {
    return new OrderResponse(
        id,
        "ORD-2026-1234ABCD",
        1,
        "user@example.com",
        status,
        new BigDecimal("50.00"),
        "USD",
        "123 Main St",
        "Note",
        null,
        null,
        List.of(new OrderItemResponse(1, "P1", "Widget", 2, new BigDecimal("25.00"), new BigDecimal("50.00"))),
        LocalDateTime.now(),
        null,
        1,
        null
    );
  }

  @Test
  void createOrderReturns201Created() throws Exception {
    when(orderService.createOrder(any(), any())).thenReturn(sampleResponse(1, OrderStatus.PENDING));

    mockMvc.perform(post("/orders")
            .with(authentication(userPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "shippingAddress": "123 Main St",
                  "notes": "Note",
                  "currency": "USD",
                  "items": [
                    {
                      "productId": "P1",
                      "productName": "Widget",
                      "quantity": 2,
                      "unitPrice": 25.00
                    }
                  ]
                }
                """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.orderNumber").value("ORD-2026-1234ABCD"))
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.totalAmount").value(50.00));
  }

  @Test
  void createOrderWithInvalidPayloadReturns400ValidationProblemDetail() throws Exception {
    mockMvc.perform(post("/orders")
            .with(authentication(userPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "shippingAddress": "",
                  "items": []
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors.shippingAddress").exists())
        .andExpect(jsonPath("$.errors.items").exists())
        .andExpect(jsonPath("$.path").value("/orders"));
  }

  @Test
  void getOrdersReturnsPagedResponsesAndBindsQueryFilterAndEnumSort() throws Exception {
    when(orderService.findAllOrders(any(OrderFilterRequest.class), any(), any()))
        .thenReturn(new PageImpl<>(List.of(sampleResponse(1, OrderStatus.PENDING))));

    mockMvc.perform(get("/orders")
            .param("status", "PENDING")
            .param("minTotal", "10.00")
            .param("sortBy", "TOTAL_AMOUNT")
            .param("sortDir", "ASC")
            .with(authentication(userPrincipal)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(1))
        .andExpect(jsonPath("$.content[0].orderNumber").value("ORD-2026-1234ABCD"));

    org.mockito.Mockito.verify(orderService).findAllOrders(
        any(OrderFilterRequest.class),
        eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "totalAmount"))),
        any()
    );
  }

  @Test
  void getOrderByIdWhenAccessDeniedReturns403ProblemDetail() throws Exception {
    when(orderService.findOrderById(eq(99), any())).thenThrow(new OrderAccessDeniedException(99));

    mockMvc.perform(get("/orders/99").with(authentication(userPrincipal)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.errorCode").value("ORDER_ACCESS_DENIED"))
        .andExpect(jsonPath("$.path").value("/orders/99"))
        .andExpect(jsonPath("$.timestamp").exists());
  }

  @Test
  void getOrderByIdWhenMissingReturns404ProblemDetail() throws Exception {
    when(orderService.findOrderById(eq(404), any())).thenThrow(new OrderNotFoundException(404));

    mockMvc.perform(get("/orders/404").with(authentication(userPrincipal)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
  }

  @Test
  void updateShippedOrderReturns409ProblemDetail() throws Exception {
    when(orderService.updateOrder(eq(5), any(), any()))
        .thenThrow(new OrderModificationNotAllowedException(OrderStatus.SHIPPED));

    mockMvc.perform(put("/orders/5")
            .with(authentication(userPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "shippingAddress": "123 Main St",
                  "currency": "USD",
                  "items": [
                    {
                      "productId": "P1",
                      "productName": "Widget",
                      "quantity": 1,
                      "unitPrice": 25.00
                    }
                  ]
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.errorCode").value("ORDER_MODIFICATION_NOT_ALLOWED"))
        .andExpect(jsonPath("$.currentStatus").value("SHIPPED"));
  }

  @Test
  void updateOrderStatusAsRegularUserIsForbiddenWith403() throws Exception {
    mockMvc.perform(patch("/orders/5/status")
            .with(authentication(userPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": "CONFIRMED"
                }
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.errorCode").value("ORDER_ACCESS_DENIED"));
  }

  @Test
  void invalidOrderStatusTransitionReturns409ProblemDetail() throws Exception {
    when(orderService.updateOrderStatus(eq(5), any(), any()))
        .thenThrow(new InvalidOrderStateTransitionException(OrderStatus.PENDING, OrderStatus.DELIVERED));

    mockMvc.perform(patch("/orders/5/status")
            .with(authentication(adminPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": "DELIVERED"
                }
                """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.errorCode").value("INVALID_ORDER_STATE"))
        .andExpect(jsonPath("$.currentStatus").value("PENDING"))
        .andExpect(jsonPath("$.targetStatus").value("DELIVERED"));
  }

  @Test
  void invalidEnumInStatusPatchReturns400MalformedRequestBody() throws Exception {
    mockMvc.perform(patch("/orders/5/status")
            .with(authentication(adminPrincipal))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "status": "NOT_A_REAL_STATUS"
                }
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST_BODY"));
  }

  @Test
  void deleteOrderSoftCancelsAndReturns200() throws Exception {
    when(orderService.cancelOrder(eq(5), eq("No longer needed"), any()))
        .thenReturn(sampleResponse(5, OrderStatus.CANCELLED));

    mockMvc.perform(delete("/orders/5")
            .with(authentication(userPrincipal))
            .param("reason", "No longer needed"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(5))
        .andExpect(jsonPath("$.status").value("CANCELLED"));
  }
}
