package com.screen.orders.order.exception;

public class EmptyOrderItemsException extends RuntimeException {

  public EmptyOrderItemsException() {
    super("Order must contain at least one item");
  }
}
