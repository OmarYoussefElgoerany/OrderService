package com.screen.orders.order.item;

import com.screen.orders.common.jpa.BaseEntity;
import com.screen.orders.order.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_items_order_id", columnList = "order_id")
    }
)
public class OrderItem extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "order_id", nullable = false)
  private Order order;

  @Column(name = "product_id", nullable = false, length = 64)
  private String productId;

  @Column(name = "product_name", nullable = false, length = 200)
  private String productName;

  @Column(nullable = false)
  private Integer quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal subtotal;

  /**
   * Computes and stores the line-item subtotal from unit price and quantity.
   */
  public BigDecimal calculateSubtotal() {
    if (unitPrice == null || quantity == null) {
      this.subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    } else {
      this.subtotal = unitPrice
          .multiply(BigDecimal.valueOf(quantity))
          .setScale(2, RoundingMode.HALF_UP);
    }
    return this.subtotal;
  }
}
