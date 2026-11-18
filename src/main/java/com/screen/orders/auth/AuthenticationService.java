package com.screen.orders.auth;

import com.screen.orders.config.JwtService;
import com.screen.orders.token.Token;
import com.screen.orders.token.TokenHash;
import com.screen.orders.token.TokenRepository;
import com.screen.orders.token.TokenType;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import com.screen.orders.user.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
  private final UserRepository repository;
  private final TokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final AuthenticationManager authenticationManager;

  @Value("${application.security.jwt.refresh-token.reuse-grace-ms:10000}")
  private long reuseGraceMs = 10000L;

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
    if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
      throw unauthorized("Refresh token is required");
    }

    final Claims claims;
    try {
      claims = jwtService.parseValidTokenClaims(presentedRefreshToken, JwtService.TYPE_REFRESH);
    } catch (JwtException | IllegalArgumentException exception) {
      throw unauthorized("Refresh token is invalid or expired");
    }

    String userEmail = claims.getSubject();
    String presentedFamilyId = claims.get(JwtService.CLAIM_SESSION_ID, String.class);
    if (userEmail == null || userEmail.isBlank() || presentedFamilyId == null || presentedFamilyId.isBlank()) {
      throw unauthorized("Refresh token is invalid or expired");
    }

    var storedSession = tokenRepository.findLockedByUserEmailAndType(userEmail, TokenType.REFRESH)
        .orElseThrow(() -> unauthorized("Refresh token is invalid, expired, or already used"));

    if (storedSession.isExpired() || storedSession.isRevoked()) {
      throw unauthorized("Refresh token is invalid, expired, or already used");
    }

    User user = storedSession.getUser();
    if (user == null || !user.isEnabled()) {
      throw unauthorized("Refresh token is invalid or expired");
    }

    // If the token belongs to an older login session family, reject without revoking the current family.
    if (!presentedFamilyId.equals(storedSession.getFamilyId())) {
      throw unauthorized("Refresh token is invalid, expired, or already used");
    }

    String presentedHash = TokenHash.sha256(presentedRefreshToken);
    Instant now = Instant.now();

    // Case 1: Active refresh token match -> standard rotation
    if (presentedHash.equals(storedSession.getTokenHash())) {
      storedSession.setPreviousTokenHash(presentedHash);
      storedSession.setRotatedAt(now);
      return issueAndSaveTokens(storedSession, user);
    }

    // Case 2: Multi-tab concurrency grace window match -> issue new token pair without extending rotatedAt
    if (isWithinConcurrencyGraceWindow(storedSession, presentedHash, now)) {
      return issueAndSaveTokens(storedSession, user);
    }

    // Case 3: Token matches current session familyId but is neither active nor within grace -> reuse detected!
    storedSession.setExpired(true);
    storedSession.setRevoked(true);
    storedSession.setPreviousTokenHash(null);
    storedSession.setRotatedAt(null);
    tokenRepository.saveAndFlush(storedSession);
    throw unauthorized("Refresh token reuse detected; the session has been revoked");
  }

  private boolean isWithinConcurrencyGraceWindow(Token session, String presentedHash, Instant now) {
    if (reuseGraceMs <= 0 || session.getPreviousTokenHash() == null || session.getRotatedAt() == null) {
      return false;
    }
    if (!presentedHash.equals(session.getPreviousTokenHash())) {
      return false;
    }
    Instant graceDeadline = session.getRotatedAt().plusMillis(reuseGraceMs);
    return !now.isAfter(graceDeadline);
  }

  private IssuedTokens createSession(User user) {
    var session = tokenRepository.findByUser_IdAndTokenType(user.getId(), TokenType.REFRESH)
        .orElseGet(() -> Token.builder().user(user).tokenType(TokenType.REFRESH).build());
    // A new login starts a fresh session family; stale tokens from prior logins cannot revoke it.
    session.setFamilyId(UUID.randomUUID().toString());
    session.setPreviousTokenHash(null);
    session.setRotatedAt(null);
    return issueAndSaveTokens(session, user);
  }

  private IssuedTokens issueAndSaveTokens(Token session, User user) {
    String accessToken = jwtService.generateToken(user);
    String refreshToken = jwtService.generateRefreshToken(user, session.getFamilyId());

    // Only hashes are persisted; clear tokens are returned once to the client.
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
