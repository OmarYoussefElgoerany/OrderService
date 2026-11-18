package com.screen.orders.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

  private final AuthenticationService service;
  @Value("${application.security.jwt.refresh-token.expiration}")
  private long refreshTokenExpirationMs;

  @PostMapping("/register")
  @Operation(security = {})
  public ResponseEntity<AuthenticationResponse> register(
      @RequestBody RegisterRequest request
  ) {
    return tokenResponse(service.register(request));
  }

  @PostMapping("/login")
  @Operation(security = {})
  public ResponseEntity<AuthenticationResponse> login(
      @RequestBody AuthenticationRequest request
  ) {
    return tokenResponse(service.login(request));
  }

  @PostMapping("/refresh")
  @Operation(security = {})
  public ResponseEntity<AuthenticationResponse> refreshToken(
      @Parameter(hidden = true)
      @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken
  ) {
    return tokenResponse(service.refresh(refreshToken));
  }

  private ResponseEntity<AuthenticationResponse> tokenResponse(IssuedTokens tokens) {
    var refreshCookie = RefreshTokenCookie.create(
        tokens.refreshToken(), refreshTokenExpirationMs
    );
    var response = AuthenticationResponse.builder()
        .accessToken(tokens.accessToken())
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(response);
  }

}
