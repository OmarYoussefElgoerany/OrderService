package com.screen.orders.auth;

import com.screen.orders.common.security.JwtService;
import com.screen.orders.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutHandler {

  private final TokenRepository tokenRepository;
  private final RefreshTokenCookie refreshTokenCookie;
  private final JwtService jwtService;

  @Override
  @Transactional
  public void logout(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication
  ) {
    response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString());

    resolveSessionToRevoke(request, authentication).ifPresent(session -> {
      session.setExpired(true);
      session.setRevoked(true);
      session.setPreviousTokenHash(null);
      session.setRotatedAt(null);
      tokenRepository.save(session);
    });
  }

  private Optional<Token> resolveSessionToRevoke(
      HttpServletRequest request,
      Authentication authentication
  ) {
    Optional<String> refreshCookieValue = RefreshTokenCookie.extractFromRequest(request);
    if (refreshCookieValue.isPresent()) {
      String rawRefresh = refreshCookieValue.get();
      String hash = TokenHash.sha256(rawRefresh);
      Optional<Token> byHash = tokenRepository.findLockedByTokenHashAndType(hash, TokenType.REFRESH);
      if (byHash.isPresent()) {
        return byHash;
      }
      Optional<Token> byRefreshClaims = resolveByJwtClaims(rawRefresh, JwtService.TYPE_REFRESH);
      if (byRefreshClaims.isPresent()) {
        return byRefreshClaims;
      }
    }

    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String rawAccess = authHeader.substring(7).trim();
      if (!rawAccess.isEmpty()) {
        Optional<Token> byAccessClaims = resolveByJwtClaims(rawAccess, JwtService.TYPE_ACCESS);
        if (byAccessClaims.isPresent()) {
          return byAccessClaims;
        }
      }
    }

    if (authentication != null && authentication.getPrincipal() instanceof User user && user.getId() != null) {
      return tokenRepository.findByUser_IdAndTokenType(user.getId(), TokenType.REFRESH);
    }

    return Optional.empty();
  }

  private Optional<Token> resolveByJwtClaims(String token, String expectedType) {
    try {
      Claims claims = jwtService.parseValidTokenClaims(token, expectedType);
      String email = claims.getSubject();
      if (email == null || email.isBlank()) {
        return Optional.empty();
      }
      return tokenRepository.findLockedByUserEmailAndType(email, TokenType.REFRESH);
    } catch (JwtException | IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }
}
