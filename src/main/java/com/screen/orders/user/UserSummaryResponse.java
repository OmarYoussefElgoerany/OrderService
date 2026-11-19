package com.screen.orders.user;

public record UserSummaryResponse(
    Integer id,
    String firstname,
    String lastname,
    String email,
    Role role
) {
}
