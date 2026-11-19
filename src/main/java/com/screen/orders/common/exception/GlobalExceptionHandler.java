package com.screen.orders.common.exception;

import com.screen.orders.order.exception.EmptyOrderItemsException;
import com.screen.orders.order.exception.InvalidOrderStateTransitionException;
import com.screen.orders.order.exception.OrderAccessDeniedException;
import com.screen.orders.order.exception.OrderModificationNotAllowedException;
import com.screen.orders.order.exception.OrderNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(OrderNotFoundException.class)
  public ProblemDetail handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
    return buildProblemDetail(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", ex.getMessage(), request);
  }

  @ExceptionHandler(OrderAccessDeniedException.class)
  public ProblemDetail handleOrderAccessDenied(OrderAccessDeniedException ex, HttpServletRequest request) {
    return buildProblemDetail(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", ex.getMessage(), request);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ProblemDetail handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
    return buildProblemDetail(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "Access denied", request);
  }

  @ExceptionHandler(InvalidOrderStateTransitionException.class)
  public ProblemDetail handleInvalidOrderStateTransition(
      InvalidOrderStateTransitionException ex,
      HttpServletRequest request
  ) {
    ProblemDetail detail = buildProblemDetail(
        HttpStatus.CONFLICT,
        "INVALID_ORDER_STATE",
        ex.getMessage(),
        request
    );
    detail.setProperty("currentStatus", ex.getCurrentStatus());
    detail.setProperty("targetStatus", ex.getTargetStatus());
    return detail;
  }

  @ExceptionHandler(OrderModificationNotAllowedException.class)
  public ProblemDetail handleOrderModificationNotAllowed(
      OrderModificationNotAllowedException ex,
      HttpServletRequest request
  ) {
    ProblemDetail detail = buildProblemDetail(
        HttpStatus.CONFLICT,
        "ORDER_MODIFICATION_NOT_ALLOWED",
        ex.getMessage(),
        request
    );
    detail.setProperty("currentStatus", ex.getCurrentStatus());
    return detail;
  }

  @ExceptionHandler(EmptyOrderItemsException.class)
  public ProblemDetail handleEmptyOrderItems(EmptyOrderItemsException ex, HttpServletRequest request) {
    return buildProblemDetail(HttpStatus.BAD_REQUEST, "EMPTY_ORDER_ITEMS", ex.getMessage(), request);
  }

  @ExceptionHandler(OptimisticLockingFailureException.class)
  public ProblemDetail handleOptimisticLockingFailure(
      OptimisticLockingFailureException ex,
      HttpServletRequest request
  ) {
    return buildProblemDetail(
        HttpStatus.CONFLICT,
        "CONCURRENT_ORDER_MODIFICATION",
        "The order was modified by another request. Please refresh and try again.",
        request
    );
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleValidationErrors(
      MethodArgumentNotValidException ex,
      HttpServletRequest request
  ) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError error : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    ProblemDetail detail = buildProblemDetail(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "Request validation failed",
        request
    );
    detail.setProperty("errors", fieldErrors);
    return detail;
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleUnreadableMessage(
      HttpMessageNotReadableException ex,
      HttpServletRequest request
  ) {
    return buildProblemDetail(
        HttpStatus.BAD_REQUEST,
        "MALFORMED_REQUEST_BODY",
        "Malformed JSON request or invalid field value",
        request
    );
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleResponseStatusException(
      ResponseStatusException ex,
      HttpServletRequest request
  ) {
    String message = (ex.getReason() != null && !ex.getReason().isBlank())
        ? ex.getReason()
        : "Request failed";
    return buildProblemDetail(ex.getStatusCode(), "REQUEST_ERROR", message, request);
  }

  private ProblemDetail buildProblemDetail(
      HttpStatusCode status,
      String errorCode,
      String message,
      HttpServletRequest request
  ) {
    ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
    String path = request != null ? request.getRequestURI() : "";
    if (!path.isEmpty()) {
      detail.setInstance(URI.create(path));
    }
    detail.setProperty("timestamp", Instant.now());
    detail.setProperty("errorCode", errorCode);
    detail.setProperty("message", message);
    detail.setProperty("path", path);
    return detail;
  }
}
