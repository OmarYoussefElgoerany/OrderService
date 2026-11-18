package com.screen.orders.auth;

import com.screen.orders.config.JwtService;
import com.screen.orders.token.Token;
import com.screen.orders.token.TokenHash;
import com.screen.orders.token.TokenRepository;
import com.screen.orders.token.TokenType;
import com.screen.orders.token.UsedRefreshToken;
import com.screen.orders.token.UsedRefreshTokenRepository;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import com.screen.orders.user.UserRepository;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
  private final UserRepository repository;
  private final TokenRepository tokenRepository;
  private final UsedRefreshTokenRepository usedRefreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  @Transactional
  public IssuedTokens register(RegisterRequest request) {
    if (repository.existsByEmail(request.getEmail())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
    }
    var user = User.builder()
        .firstname(request.getFirstname())
        .lastname(request.getLastname())
        .email(request.getEmail())
        .password(passwordEncoder.encode(request.getPassword()))
        // Public registration must never grant privileged roles.
        .role(Role.USER)
        .build();
    var savedUser = repository.save(user);
    return createSession(savedUser);
  }

  @Transactional
  public IssuedTokens login(AuthenticationRequest request) {
    try {
      authenticationManager.authenticate(
          new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
      );
    } catch (AuthenticationException exception) {
      throw unauthorized("Invalid email or password");
    }
    var user = repository.findByEmail(request.getEmail())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    return createSession(user);
  }

  @Transactional(noRollbackFor = ResponseStatusException.class)
  public IssuedTokens refresh(String presentedRefreshToken) {
    try {
      if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
        throw unauthorized("Refresh token is required");
      }
      Instant now = Instant.now();
      usedRefreshTokenRepository.deleteExpiredBefore(now);
      String presentedHash = TokenHash.sha256(presentedRefreshToken);
      var storedSessionOptional = tokenRepository.findLockedByTokenHashAndType(
          presentedHash, TokenType.REFRESH);

      if (storedSessionOptional.isEmpty()) {
        var previouslyUsed = usedRefreshTokenRepository.findLockedByTokenHash(presentedHash);
        if (previouslyUsed.isPresent()) {
          Integer sessionId = previouslyUsed.get().getSession().getId();
          tokenRepository.findLockedById(sessionId).ifPresent(session -> {
            session.setExpired(true);
            session.setRevoked(true);
            tokenRepository.saveAndFlush(session);
          });
          throw unauthorized("Refresh token reuse detected; the session has been revoked");
        }
        throw unauthorized("Refresh token is invalid, expired, or already used");
      }

      var storedSession = storedSessionOptional.get();
      if (storedSession.isExpired() || storedSession.isRevoked()) {
        throw unauthorized("Refresh token is invalid, expired, or already used");
      }

      User user = storedSession.getUser();
      if (!jwtService.isTokenValid(presentedRefreshToken, user, "refresh")) {
        throw unauthorized("Refresh token is invalid or expired");
      }

      usedRefreshTokenRepository.save(UsedRefreshToken.builder()
          .tokenHash(presentedHash)
          .expiresAt(jwtService.extractExpiration(presentedRefreshToken).toInstant())
          .session(storedSession)
          .build());
      return rotateSession(storedSession, user);
    } catch (JwtException | IllegalArgumentException exception) {
      throw unauthorized("Refresh token is invalid or expired");
    }
  }

  private IssuedTokens createSession(User user) {
    var session = tokenRepository.findByUser_IdAndTokenType(user.getId(), TokenType.REFRESH)
        .orElseGet(() -> Token.builder().user(user).tokenType(TokenType.REFRESH).build());
    if (session.getId() != null) {
      // A new login starts a new session family; stale tokens from the old login must not revoke it.
      usedRefreshTokenRepository.deleteAllBySessionId(session.getId());
    }
    return rotateSession(session, user);
  }

  private IssuedTokens rotateSession(Token session, User user) {
    String accessToken = jwtService.generateToken(user);
    String refreshToken = jwtService.generateRefreshToken(user);

    // Only hashes are persisted; the clear tokens are returned once to the client.
    session.setTokenHash(TokenHash.sha256(refreshToken));
    session.setTokenType(TokenType.REFRESH);
    session.setExpired(false);
    session.setRevoked(false);
    tokenRepository.save(session);

    return new IssuedTokens(accessToken, refreshToken);
  }

  private static ResponseStatusException unauthorized(String message) {
    return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
  }
}
