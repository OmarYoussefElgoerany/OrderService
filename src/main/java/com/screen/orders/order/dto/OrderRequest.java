package com.screen.orders.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record OrderRequest(
    @NotBlank(message = "Shipping address is required")
    @Size(max = 500, message = "Shipping address must not exceed 500 characters")
    String shippingAddress,

    @Size(max = 500, message = "Notes must not exceed 500 characters")
    String notes,

    @Pattern(regexp = "^[A-Za-z]{3}$", message = "Currency must be a 3-letter ISO code")
    String currency,

    @NotEmpty(message = "Order must contain at least one item")
    List<@Valid OrderItemRequest> items
) {
}
