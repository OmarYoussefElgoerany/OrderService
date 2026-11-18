package com.screen.orders.auth;

import com.screen.orders.config.JwtService;
import com.screen.orders.token.Token;
import com.screen.orders.token.TokenHash;
import com.screen.orders.token.TokenRepository;
import com.screen.orders.token.TokenType;
import com.screen.orders.user.Role;
import com.screen.orders.user.User;
import com.screen.orders.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

  private static final String TEST_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // 32 bytes Base64

  @Mock
  private UserRepository userRepository;
  @Mock
  private TokenRepository tokenRepository;
  @Mock
  private AuthenticationManager authenticationManager;

  private JwtService jwtService;
  private PasswordEncoder passwordEncoder;
  private AuthenticationService authenticationService;
  private User testUser;

  @BeforeEach
  void setUp() {
    jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET);
    ReflectionTestUtils.setField(jwtService, "jwtExpiration", 900_000L);
    ReflectionTestUtils.setField(jwtService, "refreshExpiration", 604_800_000L);

    passwordEncoder = new BCryptPasswordEncoder();
    authenticationService = new AuthenticationService(
        userRepository,
        tokenRepository,
        passwordEncoder,
        jwtService,
        authenticationManager
    );
    ReflectionTestUtils.setField(authenticationService, "reuseGraceMs", 10_000L);

    testUser = User.builder()
        .id(1)
        .firstname("Ada")
        .lastname("Lovelace")
        .email("ada@example.com")
        .password(passwordEncoder.encode("secret123"))
        .role(Role.USER)
        .build();

    lenient().when(tokenRepository.save(any(Token.class))).thenAnswer(inv -> inv.getArgument(0));
    lenient().when(tokenRepository.saveAndFlush(any(Token.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void registerCreatesSingleSessionWithFamilyIdAndHashedToken() {
    RegisterRequest request = RegisterRequest.builder()
        .firstname("Ada")
        .lastname("Lovelace")
        .email("ada@example.com")
        .password("secret123")
        .build();

    when(userRepository.existsByEmail("ada@example.com")).thenReturn(false);
    when(userRepository.save(any(User.class))).thenReturn(testUser);
    when(tokenRepository.findByUser_IdAndTokenType(1, TokenType.REFRESH)).thenReturn(Optional.empty());

    IssuedTokens issued = authenticationService.register(request);

    assertNotNull(issued.accessToken());
    assertNotNull(issued.refreshToken());
    String familyId = jwtService.extractSessionId(issued.refreshToken());
    assertNotNull(familyId);
  }

  @Test
  void refreshRotatesActiveTokenAndRecordsPreviousTokenHash() {
    String familyId = UUID.randomUUID().toString();
    String rt1 = jwtService.generateRefreshToken(testUser, familyId);
    String rt1Hash = TokenHash.sha256(rt1);

    Token session = Token.builder()
        .id(10)
        .user(testUser)
        .tokenType(TokenType.REFRESH)
        .familyId(familyId)
        .tokenHash(rt1Hash)
        .expired(false)
        .revoked(false)
        .build();

    when(tokenRepository.findLockedByUserEmailAndType("ada@example.com", TokenType.REFRESH))
        .thenReturn(Optional.of(session));

    IssuedTokens rotated = authenticationService.refresh(rt1);

    assertNotNull(rotated.accessToken());
    assertNotNull(rotated.refreshToken());
    assertNotEquals(rt1, rotated.refreshToken());
    assertEquals(familyId, jwtService.extractSessionId(rotated.refreshToken()));
    assertEquals(rt1Hash, session.getPreviousTokenHash());
    assertEquals(TokenHash.sha256(rotated.refreshToken()), session.getTokenHash());
    assertNotNull(session.getRotatedAt());
    assertFalse(session.isRevoked());
  }

  @Test
  void concurrentMultiTabRefreshWithinGraceWindowSucceedsWithoutExtendingRotatedAt() {
    String familyId = UUID.randomUUID().toString();
    String rt1 = jwtService.generateRefreshToken(testUser, familyId);
    String rt1Hash = TokenHash.sha256(rt1);
    String rt2 = jwtService.generateRefreshToken(testUser, familyId);
    Instant firstRotationTime = Instant.now().minusMillis(1500);

    Token session = Token.builder()
        .id(10)
        .user(testUser)
        .tokenType(TokenType.REFRESH)
        .familyId(familyId)
        .tokenHash(TokenHash.sha256(rt2))
        .previousTokenHash(rt1Hash)
        .rotatedAt(firstRotationTime)
        .expired(false)
        .revoked(false)
        .build();

    when(tokenRepository.findLockedByUserEmailAndType("ada@example.com", TokenType.REFRESH))
        .thenReturn(Optional.of(session));

    IssuedTokens tabBTokens = authenticationService.refresh(rt1);

    assertNotNull(tabBTokens.accessToken());
    assertNotNull(tabBTokens.refreshToken());
    assertFalse(session.isRevoked());
    assertFalse(session.isExpired());
    // Grace window timestamp and previousTokenHash must not be extended
    assertEquals(firstRotationTime, session.getRotatedAt());
    assertEquals(rt1Hash, session.getPreviousTokenHash());
    assertEquals(TokenHash.sha256(tabBTokens.refreshToken()), session.getTokenHash());
  }

  @Test
  void refreshReuseAfterGraceWindowRevokesSessionAndThrows401() {
    String familyId = UUID.randomUUID().toString();
    String rt1 = jwtService.generateRefreshToken(testUser, familyId);
    String rt1Hash = TokenHash.sha256(rt1);
    String rt2 = jwtService.generateRefreshToken(testUser, familyId);
    Instant expiredGraceTime = Instant.now().minusMillis(15_000); // > 10s grace window

    Token session = Token.builder()
        .id(10)
        .user(testUser)
        .tokenType(TokenType.REFRESH)
        .familyId(familyId)
        .tokenHash(TokenHash.sha256(rt2))
        .previousTokenHash(rt1Hash)
        .rotatedAt(expiredGraceTime)
        .expired(false)
        .revoked(false)
        .build();

    when(tokenRepository.findLockedByUserEmailAndType("ada@example.com", TokenType.REFRESH))
        .thenReturn(Optional.of(session));

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> authenticationService.refresh(rt1)
    );

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    assertTrue(session.isRevoked());
    assertTrue(session.isExpired());
    assertNull(session.getPreviousTokenHash());
    assertNull(session.getRotatedAt());
    verify(tokenRepository).saveAndFlush(session);
  }

  @Test
  void staleRefreshTokenFromOlderLoginFamilyDoesNotRevokeActiveSession() {
    String oldFamilyId = UUID.randomUUID().toString();
    String activeFamilyId = UUID.randomUUID().toString();
    String oldRefreshToken = jwtService.generateRefreshToken(testUser, oldFamilyId);
    String activeRefreshToken = jwtService.generateRefreshToken(testUser, activeFamilyId);

    Token activeSession = Token.builder()
        .id(10)
        .user(testUser)
        .tokenType(TokenType.REFRESH)
        .familyId(activeFamilyId)
        .tokenHash(TokenHash.sha256(activeRefreshToken))
        .expired(false)
        .revoked(false)
        .build();

    when(tokenRepository.findLockedByUserEmailAndType("ada@example.com", TokenType.REFRESH))
        .thenReturn(Optional.of(activeSession));

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> authenticationService.refresh(oldRefreshToken)
    );

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    assertFalse(activeSession.isRevoked());
    assertFalse(activeSession.isExpired());
  }

  @Test
  void invalidOrAccessTypedTokenFailsInMemoryWithoutQueryingDatabase() {
    String accessToken = jwtService.generateToken(testUser);

    ResponseStatusException ex = assertThrows(
        ResponseStatusException.class,
        () -> authenticationService.refresh(accessToken)
    );

    assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    verify(tokenRepository, never()).findLockedByUserEmailAndType(any(), any());
  }
}
