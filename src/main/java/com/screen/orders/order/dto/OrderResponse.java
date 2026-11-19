package com.screen.orders.order.dto;

import com.screen.orders.order.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
    Integer id,
    String orderNumber,
    Integer userId,
    String userEmail,
    OrderStatus status,
    BigDecimal totalAmount,
    String currency,
    String shippingAddress,
    String notes,
    LocalDateTime cancelledAt,
    String cancellationReason,
    List<OrderItemResponse> items,
    LocalDateTime createDate,
    LocalDateTime lastModified,
    Integer createdBy,
    Integer lastModifiedBy
) {
}
