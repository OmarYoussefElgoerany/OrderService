package com.screen.orders.order.exception;

import com.screen.orders.order.OrderStatus;
import lombok.Getter;

@Getter
public class InvalidOrderStateTransitionException extends RuntimeException {

  private final OrderStatus currentStatus;
  private final OrderStatus targetStatus;

  public InvalidOrderStateTransitionException(OrderStatus currentStatus, OrderStatus targetStatus) {
    super("Invalid order state transition from " + currentStatus + " to " + targetStatus);
    this.currentStatus = currentStatus;
    this.targetStatus = targetStatus;
  }
}
