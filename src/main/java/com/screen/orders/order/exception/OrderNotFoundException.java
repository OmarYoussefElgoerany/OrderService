package com.screen.orders.order.exception;

public class OrderNotFoundException extends RuntimeException {

  public OrderNotFoundException(Integer id) {
    super("Order not found with id: " + id);
  }

  public OrderNotFoundException(String orderNumber) {
    super("Order not found with orderNumber: " + orderNumber);
  }
}
