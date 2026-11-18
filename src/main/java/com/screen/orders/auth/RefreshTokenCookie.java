package com.screen.orders.auth;

import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class RefreshTokenCookie {

  public static final String NAME = "refreshToken";
  public static final String PATH = "/api/auth/refresh";

  private RefreshTokenCookie() {
  }

  public static ResponseCookie create(String value, long maxAgeMillis) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("None")
        .path(PATH)
        .maxAge(Duration.ofMillis(maxAgeMillis))
        .build();
  }

  public static ResponseCookie clear() {
    return ResponseCookie.from(NAME, "")
        .httpOnly(true)
        .secure(true)
        .sameSite("None")
        .path(PATH)
        .maxAge(Duration.ZERO)
        .build();
  }
}
