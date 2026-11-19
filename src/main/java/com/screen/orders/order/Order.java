package com.screen.orders.order;

import com.screen.orders.common.jpa.BaseAuditedEntity;
import com.screen.orders.order.exception.EmptyOrderItemsException;
import com.screen.orders.order.exception.InvalidOrderStateTransitionException;
import com.screen.orders.order.exception.OrderModificationNotAllowedException;
import com.screen.orders.order.item.OrderItem;
import com.screen.orders.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "orders",
    indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_order_number", columnList = "order_number", unique = true),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_create_date", columnList = "createDate")
    }
)
public class Order extends BaseAuditedEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "order_number", nullable = false, unique = true, length = 40)
  private String orderNumber;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private OrderStatus status = OrderStatus.PENDING;

  @Builder.Default
  @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
  private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

  @Builder.Default
  @Column(nullable = false, length = 3)
  private String currency = "USD";

  @Column(name = "shipping_address", nullable = false, length = 500)
  private String shippingAddress;

  @Column(length = 500)
  private String notes;

  @Column(name = "cancelled_at")
  private LocalDateTime cancelledAt;

  @Column(name = "cancellation_reason", length = 500)
  private String cancellationReason;

  @Version
  private Long version;

  @Builder.Default
  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> items = new ArrayList<>();

  public void addItem(OrderItem item) {
    if (item == null) {
      return;
    }
    if (this.items == null) {
      this.items = new ArrayList<>();
    }
    item.setOrder(this);
    item.calculateSubtotal();
    this.items.add(item);
    recalculateTotal();
  }

  public void removeItem(OrderItem item) {
    if (item == null || this.items == null) {
      return;
    }
    this.items.remove(item);
    item.setOrder(null);
    recalculateTotal();
  }

  public void replaceItems(List<OrderItem> newItems) {
    if (newItems == null || newItems.isEmpty()) {
      throw new EmptyOrderItemsException();
    }
    if (this.items == null) {
      this.items = new ArrayList<>();
    }
    this.items.forEach(existing -> existing.setOrder(null));
    this.items.clear();
    for (OrderItem item : newItems) {
      item.setOrder(this);
      item.calculateSubtotal();
      this.items.add(item);
    }
    recalculateTotal();
  }

  public void recalculateTotal() {
    if (this.items == null || this.items.isEmpty()) {
      this.totalAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
      return;
    }
    this.totalAmount = this.items.stream()
        .map(OrderItem::calculateSubtotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Updates mutable order details. Permitted only while the order is in {@link OrderStatus#PENDING}.
   */
  public void updateDetails(
      String shippingAddress,
      String notes,
      String currency,
      List<OrderItem> newItems
  ) {
    if (this.status != OrderStatus.PENDING) {
      throw new OrderModificationNotAllowedException(this.status);
    }
    if (newItems == null || newItems.isEmpty()) {
      throw new EmptyOrderItemsException();
    }
    this.shippingAddress = shippingAddress;
    this.notes = notes;
    if (currency != null && !currency.isBlank()) {
      this.currency = currency.trim().toUpperCase();
    }
    replaceItems(newItems);
  }

  /**
   * Cancels the order under standard customer rules (allowed only in PENDING or CONFIRMED).
   */
  public void cancel(String reason) {
    cancel(reason, false);
  }

  /**
   * Soft-cancels the order while preserving audit history.
   *
   * @param reason cancellation reason
   * @param privileged true for ADMIN/MANAGEMENT (any non-delivered, non-cancelled order),
   *                   false for customer (PENDING or CONFIRMED only)
   */
  public void cancel(String reason, boolean privileged) {
    if (this.status == OrderStatus.DELIVERED || this.status == OrderStatus.CANCELLED) {
      throw new InvalidOrderStateTransitionException(this.status, OrderStatus.CANCELLED);
    }
    if (!privileged && this.status != OrderStatus.PENDING && this.status != OrderStatus.CONFIRMED) {
      throw new InvalidOrderStateTransitionException(this.status, OrderStatus.CANCELLED);
    }
    this.status = OrderStatus.CANCELLED;
    this.cancelledAt = LocalDateTime.now();
    this.cancellationReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Cancelled";
  }

  /**
   * Transitions the order to the next lifecycle status using the state-machine guard.
   */
  public void transitionTo(OrderStatus nextStatus) {
    if (nextStatus == OrderStatus.CANCELLED) {
      cancel("Cancelled via status transition", true);
      return;
    }
    if (this.status == null || !this.status.canTransitionTo(nextStatus)) {
      throw new InvalidOrderStateTransitionException(this.status, nextStatus);
    }
    this.status = nextStatus;
  }
}
