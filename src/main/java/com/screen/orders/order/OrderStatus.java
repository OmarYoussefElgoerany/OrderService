package com.screen.orders.order;

public enum OrderStatus {
  PENDING,
  CONFIRMED,
  PROCESSING,
  SHIPPED,
  DELIVERED,
  CANCELLED;

  /**
   * Validates strict forward state-machine transitions for staff status updates.
   * Cancellations are handled explicitly via {@link Order#cancel(String, boolean)}.
   */
  public boolean canTransitionTo(OrderStatus target) {
    if (target == null) {
      return false;
    }
    return switch (this) {
      case PENDING -> target == CONFIRMED;
      case CONFIRMED -> target == PROCESSING;
      case PROCESSING -> target == SHIPPED;
      case SHIPPED -> target == DELIVERED;
      case DELIVERED, CANCELLED -> false;
    };
  }
}
