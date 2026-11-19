package com.screen.orders.order.exception;

public class OrderAccessDeniedException extends RuntimeException {

  public OrderAccessDeniedException(Integer orderId) {
    super("Access denied for order id: " + orderId);
  }

  public OrderAccessDeniedException(String message) {
    super(message);
  }
}
