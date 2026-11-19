package com.screen.orders.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
public class RefreshTokenCookie {

  public static final String NAME = "refreshToken";
  public static final String PATH = "/api/v1/auth";

  private final long refreshTokenExpirationMs;
  private final boolean secure;
  private final String sameSite;

  public RefreshTokenCookie(
      @Value("${application.security.jwt.refresh-token.expiration}") long refreshTokenExpirationMs,
      @Value("${application.security.jwt.refresh-token.cookie-secure:true}") boolean secure,
      @Value("${application.security.jwt.refresh-token.cookie-same-site:Lax}") String sameSite
  ) {
    this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    this.secure = secure;
    this.sameSite = sameSite;
  }

  public ResponseCookie create(String value) {
    return buildCookie(value, Duration.ofMillis(refreshTokenExpirationMs), secure, sameSite);
  }

  public ResponseCookie clear() {
    return buildCookie("", Duration.ZERO, secure, sameSite);
  }

  public static ResponseCookie create(String value, long maxAgeMillis) {
    return buildCookie(value, Duration.ofMillis(maxAgeMillis), true, "Lax");
  }

  public static Optional<String> extractFromRequest(HttpServletRequest request) {
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    return Arrays.stream(cookies)
        .filter(cookie -> NAME.equals(cookie.getName()))
        .map(Cookie::getValue)
        .filter(val -> val != null && !val.isBlank())
        .findFirst();
  }

  private static ResponseCookie buildCookie(
      String value,
      Duration maxAge,
      boolean secure,
      String sameSite
  ) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(secure)
        .sameSite(sameSite)
        .path(PATH)
        .maxAge(maxAge)
        .build();
  }
}
