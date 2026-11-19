package com.screen.orders.auth;

import com.screen.orders.config.LogoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/auth")
public class AuthenticationController {

  private final AuthenticationService service;
  private final RefreshTokenCookie refreshTokenCookie;
  private final LogoutService logoutService;
  private final Set<String> allowedOrigins;

  public AuthenticationController(
      AuthenticationService service,
      RefreshTokenCookie refreshTokenCookie,
      LogoutService logoutService,
      @Value("${application.cors.allowed-origins:http://localhost:3000}") String allowedOriginsProperty
  ) {
    this.service = service;
    this.refreshTokenCookie = refreshTokenCookie;
    this.logoutService = logoutService;
    this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .collect(Collectors.toUnmodifiableSet());
  }

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
      @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
      HttpServletRequest request
  ) {
    enforceCsrfProtection(request);
    return tokenResponse(service.refresh(refreshToken));
  }

  @PostMapping("/logout")
  @Operation(security = {})
  public ResponseEntity<Void> logout(
      HttpServletRequest request,
      HttpServletResponse response
  ) {
    enforceCsrfProtection(request);
    logoutService.logout(request, response, SecurityContextHolder.getContext().getAuthentication());
    SecurityContextHolder.clearContext();
    return ResponseEntity.ok().build();
  }

  private void enforceCsrfProtection(HttpServletRequest request) {
    String origin = request.getHeader(HttpHeaders.ORIGIN);
    if (origin != null && !origin.isBlank()) {
      if ("null".equalsIgnoreCase(origin.trim()) || !isTrustedOrigin(origin.trim(), request)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Untrusted Origin header");
      }
      return;
    }

    String requestedWith = request.getHeader("X-Requested-With");
    if (requestedWith == null || requestedWith.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Refresh request requires a trusted Origin or X-Requested-With header"
      );
    }
  }

  private boolean isTrustedOrigin(String origin, HttpServletRequest request) {
    if (allowedOrigins.contains(origin)) {
      return true;
    }
    String scheme = request.getScheme();
    String host = request.getServerName();
    int port = request.getServerPort();
    if (scheme == null || host == null) {
      return false;
    }
    String serverOriginWithPort = scheme + "://" + host + ":" + port;
    String serverOriginDefaultPort = scheme + "://" + host;
    boolean isDefaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
        || ("https".equalsIgnoreCase(scheme) && port == 443);
    return origin.equalsIgnoreCase(serverOriginWithPort)
        || (isDefaultPort && origin.equalsIgnoreCase(serverOriginDefaultPort));
  }

  private ResponseEntity<AuthenticationResponse> tokenResponse(IssuedTokens tokens) {
    var refreshCookie = refreshTokenCookie.create(tokens.refreshToken());
    var response = AuthenticationResponse.builder()
        .accessToken(tokens.accessToken())
        .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(response);
  }

}
