package com.screen.orders.order;

import com.screen.orders.order.dto.OrderFilterRequest;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class OrderSpecification {

  private OrderSpecification() {
  }

  static Specification<Order> withFilters(OrderFilterRequest filter, Integer effectiveUserId) {
    OrderFilterRequest activeFilter = filter != null ? filter : OrderFilterRequest.empty();

    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      if (effectiveUserId != null) {
        predicates.add(cb.equal(root.get("user").get("id"), effectiveUserId));
      }
      if (activeFilter.status() != null) {
        predicates.add(cb.equal(root.get("status"), activeFilter.status()));
      }
      if (activeFilter.orderNumber() != null && !activeFilter.orderNumber().isBlank()) {
        String pattern = "%" + activeFilter.orderNumber().trim().toLowerCase(Locale.ROOT) + "%";
        predicates.add(cb.like(cb.lower(root.get("orderNumber")), pattern));
      }
      if (activeFilter.minTotal() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("totalAmount"), activeFilter.minTotal()));
      }
      if (activeFilter.maxTotal() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("totalAmount"), activeFilter.maxTotal()));
      }
      if (activeFilter.fromDate() != null) {
        predicates.add(cb.greaterThanOrEqualTo(root.get("createDate"), activeFilter.fromDate()));
      }
      if (activeFilter.toDate() != null) {
        predicates.add(cb.lessThanOrEqualTo(root.get("createDate"), activeFilter.toDate()));
      }

      return cb.and(predicates.toArray(Predicate[]::new));
    };
  }
}
