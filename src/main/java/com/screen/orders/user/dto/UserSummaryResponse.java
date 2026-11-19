package com.screen.orders.user.dto;

import com.screen.orders.user.Role;

public record UserSummaryResponse(
    Integer id,
    String firstname,
    String lastname,
    String email,
    Role role
) {
}
