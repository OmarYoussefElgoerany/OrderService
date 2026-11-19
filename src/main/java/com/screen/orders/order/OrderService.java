package com.screen.orders.order;

import com.screen.orders.order.dto.OrderFilterRequest;
import com.screen.orders.order.dto.OrderRequest;
import com.screen.orders.order.dto.OrderResponse;
import com.screen.orders.order.dto.OrderStatusUpdateRequest;
import com.screen.orders.order.exception.EmptyOrderItemsException;
import com.screen.orders.order.exception.OrderAccessDeniedException;
import com.screen.orders.order.exception.OrderNotFoundException;
import com.screen.orders.order.item.OrderItem;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

  private static final int MAX_PAGE_SIZE = 100;
  private static final String DEFAULT_CURRENCY = "USD";
  private static final Map<String, String> ALLOWED_SORT_FIELDS = Map.of(
      "id", "id",
      "orderNumber", "orderNumber",
      "status", "status",
      "totalAmount", "totalAmount",
      "createDate", "createDate",
      "createdDate", "createDate",
      "lastModified", "lastModified"
  );

  private final OrderRepository repository;
  private final OrderMapper orderMapper;

  @Transactional
  public OrderResponse createOrder(OrderRequest request, Principal connectedUser) {
    User currentUser = extractAuthenticatedUser(connectedUser);
    if (request == null || request.items() == null || request.items().isEmpty()) {
      throw new EmptyOrderItemsException();
    }

    String currency = (request.currency() != null && !request.currency().isBlank())
        ? request.currency().trim().toUpperCase(Locale.ROOT)
        : DEFAULT_CURRENCY;

    Order order = Order.builder()
        .user(currentUser)
        .orderNumber(generateOrderNumber())
        .status(OrderStatus.PENDING)
        .currency(currency)
        .shippingAddress(request.shippingAddress().trim())
        .notes(request.notes())
        .build();

    List<OrderItem> items = orderMapper.toItemEntities(request.items());
    for (OrderItem item : items) {
      order.addItem(item);
    }

    Order saved = repository.save(order);
    return orderMapper.toResponse(saved);
  }

  @Transactional(readOnly = true)
  public Page<OrderResponse> findAllOrders(
      OrderFilterRequest filter,
      Pageable pageable,
      Principal connectedUser
  ) {
    User currentUser = extractAuthenticatedUser(connectedUser);
    OrderFilterRequest activeFilter = filter != null ? filter : OrderFilterRequest.empty();
    Integer effectiveUserId = isPrivileged(currentUser) ? activeFilter.userId() : currentUser.getId();

    Pageable sanitizedPageable = sanitizePageable(pageable);
    Specification<Order> spec = OrderSpecification.withFilters(activeFilter, effectiveUserId);

    return repository.findAll(spec, sanitizedPageable).map(orderMapper::toResponse);
  }

  @Transactional(readOnly = true)
  public OrderResponse findOrderById(Integer id, Principal connectedUser) {
    User currentUser = extractAuthenticatedUser(connectedUser);
    Order order = repository.findWithDetailsById(id)
        .orElseThrow(() -> new OrderNotFoundException(id));
    assertCanAccessOrder(order, currentUser);
    return orderMapper.toResponse(order);
  }

  @Transactional
  public OrderResponse updateOrder(Integer id, OrderRequest request, Principal connectedUser) {
    User currentUser = extractAuthenticatedUser(connectedUser);
    Order order = repository.findWithDetailsById(id)
        .orElseThrow(() -> new OrderNotFoundException(id));
    assertCanAccessOrder(order, currentUser);

    if (request == null || request.items() == null || request.items().isEmpty()) {
      throw new EmptyOrderItemsException();
    }

    List<OrderItem> newItems = orderMapper.toItemEntities(request.items());
    order.updateDetails(
        request.shippingAddress().trim(),
        request.notes(),
        request.currency(),
        newItems
    );

    Order saved = repository.save(order);
    return orderMapper.toResponse(saved);
  }

  @Transactional
  public OrderResponse updateOrderStatus(
      Integer id,
      OrderStatusUpdateRequest request,
      Principal connectedUser
  ) {
    extractAuthenticatedUser(connectedUser);
    Order order = repository.findWithDetailsById(id)
        .orElseThrow(() -> new OrderNotFoundException(id));

    order.transitionTo(request.status());
    Order saved = repository.save(order);
    return orderMapper.toResponse(saved);
  }

  @Transactional
  public OrderResponse cancelOrder(Integer id, String reason, Principal connectedUser) {
    User currentUser = extractAuthenticatedUser(connectedUser);
    Order order = repository.findWithDetailsById(id)
        .orElseThrow(() -> new OrderNotFoundException(id));
    assertCanAccessOrder(order, currentUser);

    order.cancel(reason, isPrivileged(currentUser));
    Order saved = repository.save(order);
    return orderMapper.toResponse(saved);
  }

  private String generateOrderNumber() {
    String prefix = "ORD-" + Year.now().getValue() + "-";
    for (int attempt = 0; attempt < 5; attempt++) {
      String suffix = UUID.randomUUID()
          .toString()
          .replace("-", "")
          .substring(0, 8)
          .toUpperCase(Locale.ROOT);
      String candidate = prefix + suffix;
      if (!repository.existsByOrderNumber(candidate)) {
        return candidate;
      }
    }
    return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
  }

  private static Pageable sanitizePageable(Pageable pageable) {
    if (pageable == null || pageable.isUnpaged()) {
      return PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createDate"));
    }
    int pageNumber = Math.max(pageable.getPageNumber(), 0);
    int pageSize = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);

    Sort sanitizedSort;
    if (pageable.getSort().isUnsorted()) {
      sanitizedSort = Sort.by(Sort.Direction.DESC, "createDate");
    } else {
      List<Sort.Order> orders = pageable.getSort().stream()
          .map(order -> {
            String mapped = ALLOWED_SORT_FIELDS.get(order.getProperty());
            if (mapped == null) {
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST,
                  "Invalid sort field: " + order.getProperty()
              );
            }
            return new Sort.Order(order.getDirection(), mapped);
          })
          .toList();
      sanitizedSort = Sort.by(orders);
    }

    return PageRequest.of(pageNumber, pageSize, sanitizedSort);
  }

  private static boolean isPrivileged(User user) {
    return user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGEMENT;
  }

  private static void assertCanAccessOrder(Order order, User currentUser) {
    if (isPrivileged(currentUser)) {
      return;
    }
    if (order.getUser() == null || !Objects.equals(order.getUser().getId(), currentUser.getId())) {
      throw new OrderAccessDeniedException(order.getId());
    }
  }

  private static User extractAuthenticatedUser(Principal connectedUser) {
    if (connectedUser instanceof Authentication auth
        && auth.getPrincipal() instanceof User user) {
      return user;
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated user is required");
  }
}
