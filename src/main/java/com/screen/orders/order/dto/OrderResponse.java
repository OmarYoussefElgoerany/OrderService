package com.screen.orders.order.dto;

import java.time.LocalDateTime;

public record OrderResponse(
    Integer id,
    String name,
    LocalDateTime createDate,
    LocalDateTime lastModified,
    Integer createdBy,
    Integer lastModifiedBy
) {
}
