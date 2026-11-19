package com.screen.orders.order.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
    Integer id,
    String productId,
    String productName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal subtotal
) {
}
