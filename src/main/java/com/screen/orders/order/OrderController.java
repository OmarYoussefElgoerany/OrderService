package com.screen.orders.order;

import com.screen.orders.order.dto.CancelOrderRequest;
import com.screen.orders.order.dto.OrderFilterRequest;
import com.screen.orders.order.dto.OrderRequest;
import com.screen.orders.order.dto.OrderResponse;
import com.screen.orders.order.dto.OrderStatusUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

  private final OrderService service;

  @PostMapping
  public ResponseEntity<OrderResponse> createOrder(
      @Valid @RequestBody OrderRequest request,
      Principal connectedUser
  ) {
    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(service.createOrder(request, connectedUser));
  }

  @GetMapping
  public ResponseEntity<Page<OrderResponse>> findAllOrders(
      @ParameterObject OrderFilterRequest filter,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "CREATE_DATE") OrderSortField sortBy,
      @RequestParam(defaultValue = "DESC") Sort.Direction sortDir,
      Principal connectedUser
  ) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 100);
    Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(sortDir, sortBy.getField()));
    return ResponseEntity.ok(service.findAllOrders(filter, pageable, connectedUser));
  }

  @GetMapping("/{id}")
  public ResponseEntity<OrderResponse> findOrderById(
      @PathVariable Integer id,
      Principal connectedUser
  ) {
    return ResponseEntity.ok(service.findOrderById(id, connectedUser));
  }

  @PutMapping("/{id}")
  public ResponseEntity<OrderResponse> updateOrder(
      @PathVariable Integer id,
      @Valid @RequestBody OrderRequest request,
      Principal connectedUser
  ) {
    return ResponseEntity.ok(service.updateOrder(id, request, connectedUser));
  }

  @PatchMapping("/{id}/status")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGEMENT')")
  public ResponseEntity<OrderResponse> updateOrderStatus(
      @PathVariable Integer id,
      @Valid @RequestBody OrderStatusUpdateRequest request,
      Principal connectedUser
  ) {
    return ResponseEntity.ok(service.updateOrderStatus(id, request, connectedUser));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<OrderResponse> cancelOrder(
      @PathVariable Integer id,
      @RequestParam(required = false) String reason,
      @Valid @RequestBody(required = false) CancelOrderRequest request,
      Principal connectedUser
  ) {
    String effectiveReason = (request != null && request.reason() != null)
        ? request.reason()
        : reason;
    return ResponseEntity.ok(service.cancelOrder(id, effectiveReason, connectedUser));
  }
}
