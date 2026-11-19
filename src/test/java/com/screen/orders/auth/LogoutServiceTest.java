package com.screen.orders.auth;

import com.screen.orders.common.security.JwtService;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

  private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

  @Mock
  private TokenRepository tokenRepository;

  private JwtService jwtService;
  private RefreshTokenCookie refreshTokenCookie;
  private LogoutService logoutService;
  private User testUser;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
    ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L);
    ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L);

    refreshTokenCookie = new RefreshTokenCookie(604_800_000L, true, "Lax");
    logoutService = new LogoutService(tokenRepository, refreshTokenCookie, jwtService);

    testUser = User.builder()
        .id(1)
        .email("ada@example.com")
        .role(Role.USER)
        .build();
  }

  @Test
  void logoutRevokesSessionViaRefreshCookieWhenAuthenticationIsNull() {
    String familyId = UUID.randomUUID().toString();
    String refreshToken = jwtService.generateRefreshToken(testUser, familyId);
    String hash = TokenHash.sha256(refreshToken);

    Token session = Token.builder()
        .id(5)
        .user(testUser)
        .tokenType(TokenType.REFRESH)
        .familyId(familyId)
        .tokenHash(hash)
        .previousTokenHash("prev")
        .rotatedAt(Instant.now())
        .expired(false)
        .revoked(false)
        .build();

    when(tokenRepository.findLockedByTokenHashAndType(hash, TokenType.REFRESH))
        .thenReturn(Optional.of(session));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie(RefreshTokenCookie.NAME, refreshToken));
    MockHttpServletResponse response = new MockHttpServletResponse();

    logoutService.logout(request, response, null);

    assertTrue(session.isRevoked());
    assertTrue(session.isExpired());
    assertNull(session.getPreviousTokenHash());
    assertNull(session.getRotatedAt());
    verify(tokenRepository).save(session);

    String setCookie = response.getHeader(HttpHeaders.SET_COOKIE);
    assertNotNull(setCookie);
    assertTrue(setCookie.contains("Path=/api/v1/auth"));
    assertTrue(setCookie.contains("Max-Age=0"));
  }

  @Test
  void logoutRevokesSessionViaBearerAccessTokenWhenCookieIsAbsent() {
    String accessToken = jwtService.generateToken(testUser);

    Token session = Token.builder()
        .id(5)
        .user(testUser)
        .tokenType(TokenType.REFRESH)
        .familyId(UUID.randomUUID().toString())
        .tokenHash("some-hash")
        .expired(false)
        .revoked(false)
        .build();

    when(tokenRepository.findLockedByUserEmailAndType("ada@example.com", TokenType.REFRESH))
        .thenReturn(Optional.of(session));

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
    MockHttpServletResponse response = new MockHttpServletResponse();

    logoutService.logout(request, response, null);

    assertTrue(session.isRevoked());
    assertTrue(session.isExpired());
    verify(tokenRepository).save(session);
  }
}
