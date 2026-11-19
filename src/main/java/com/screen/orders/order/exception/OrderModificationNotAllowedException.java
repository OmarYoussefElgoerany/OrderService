package com.screen.orders.order.exception;

import com.screen.orders.order.OrderStatus;
import lombok.Getter;

@Getter
public class OrderModificationNotAllowedException extends RuntimeException {

  private final OrderStatus currentStatus;

  public OrderModificationNotAllowedException(OrderStatus currentStatus) {
    super("Order cannot be modified when status is " + currentStatus);
    this.currentStatus = currentStatus;
  }
}
