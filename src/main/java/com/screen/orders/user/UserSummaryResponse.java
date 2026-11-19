package com.screen.orders.user;

public record UserSummaryResponse(
    Integer id,
    String firstname,
    String lastname,
    String email,
    Role role
) {
  public static UserSummaryResponse from(User user) {
    return new UserSummaryResponse(
        user.getId(),
        user.getFirstname(),
        user.getLastname(),
        user.getEmail(),
        user.getRole()
    );
  }
}
