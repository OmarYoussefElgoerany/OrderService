package com.screen.orders.config;

import com.screen.orders.auth.RefreshTokenCookie;
import com.screen.orders.token.TokenRepository;
import com.screen.orders.token.TokenType;
import com.screen.orders.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogoutService implements LogoutHandler {

  private final TokenRepository tokenRepository;

  @Override
  @Transactional
  public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
    response.addHeader(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear().toString());
    if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
      return;
    }

    tokenRepository.findByUser_IdAndTokenType(user.getId(), TokenType.REFRESH).ifPresent(session -> {
      session.setExpired(true);
      session.setRevoked(true);
      tokenRepository.save(session);
    });
  }
}
