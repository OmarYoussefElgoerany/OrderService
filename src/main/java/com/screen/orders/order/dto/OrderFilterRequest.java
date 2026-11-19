package com.screen.orders.order.dto;

import com.screen.orders.order.OrderStatus;
import lombok.Builder;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record OrderFilterRequest(
    OrderStatus status,
    String orderNumber,
    BigDecimal minTotal,
    BigDecimal maxTotal,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime fromDate,
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    LocalDateTime toDate,
    Integer userId
) {

  public static OrderFilterRequest empty() {
    return new OrderFilterRequest(null, null, null, null, null, null, null);
  }

  public static OrderFilterRequest byStatus(OrderStatus status) {
    return new OrderFilterRequest(status, null, null, null, null, null, null);
  }
}
