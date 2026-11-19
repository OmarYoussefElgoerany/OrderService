package com.screen.orders.auth;

import com.screen.orders.config.LogoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

  @Mock
  private AuthenticationService authenticationService;
  @Mock
  private LogoutService logoutService;

  private RefreshTokenCookie refreshTokenCookie;
  private AuthenticationController controller;

  @BeforeEach
  void setUp() {
    refreshTokenCookie = new RefreshTokenCookie(604_800_000L, true, "Lax");
    controller = new AuthenticationController(
        authenticationService,
        refreshTokenCookie,
        logoutService,
        "http://localhost:3000,https://app.example.com"
    );
  }

  @Test
  void refreshAllowsTrustedConfiguredOrigin() {
    when(authenticationService.refresh("rt-1")).thenReturn(new IssuedTokens("at-2", "rt-2"));

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
    request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");

    ResponseEntity<AuthenticationResponse> response = controller.refreshToken("rt-1", request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("at-2", response.getBody().getAccessToken());
    String cookieHeader = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertNotNull(cookieHeader);
    assertTrue(cookieHeader.contains("Path=/api/v1/auth"));
    assertTrue(cookieHeader.contains("SameSite=Lax"));
  }

  @Test
  void refreshRejectsUntrustedOriginWith403() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
    request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> controller.refreshToken("rt-1", request)
    );

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    verify(authenticationService, never()).refresh(any());
  }

  @Test
  void refreshRejectsMissingOriginAndMissingCustomHeaderWith403() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> controller.refreshToken("rt-1", request)
    );

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    verify(authenticationService, never()).refresh(any());
  }

  @Test
  void refreshAllowsRequestWithoutOriginWhenXRequestedWithHeaderIsPresent() {
    when(authenticationService.refresh("rt-1")).thenReturn(new IssuedTokens("at-2", "rt-2"));

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
    request.addHeader("X-Requested-With", "XMLHttpRequest");

    ResponseEntity<AuthenticationResponse> response = controller.refreshToken("rt-1", request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
  }

  @Test
  void logoutAllowsTrustedOriginAndDelegatesToLogoutService() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
    request.addHeader(HttpHeaders.ORIGIN, "http://localhost:3000");
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseEntity<Void> result = controller.logout(request, response);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    verify(logoutService).logout(request, response, null);
  }

  @Test
  void logoutRejectsUntrustedOriginWith403() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/logout");
    request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
    MockHttpServletResponse response = new MockHttpServletResponse();

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> controller.logout(request, response)
    );

    assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    verify(logoutService, never()).logout(any(), any(), any());
  }
}
