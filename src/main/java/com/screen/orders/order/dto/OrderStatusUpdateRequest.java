package com.screen.orders.order.dto;

import com.screen.orders.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderStatusUpdateRequest(
    @NotNull(message = "Target status is required")
    OrderStatus status
) {
}
