package com.screen.orders.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

  public static final String CLAIM_TOKEN_TYPE = "token_type";
  public static final String CLAIM_SESSION_ID = "sid";
  public static final String CLAIM_ROLE = "role";
  public static final String TYPE_ACCESS = "access";
  public static final String TYPE_REFRESH = "refresh";

  @Value("${application.security.jwt.secret-key}")
  private String secretKey;
  @Value("${application.security.jwt.expiration}")
  private long jwtExpiration;
  @Value("${application.security.jwt.refresh-token.expiration}")
  private long refreshExpiration;

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
  }

  public String extractSessionId(String token) {
    return extractClaim(token, claims -> claims.get(CLAIM_SESSION_ID, String.class));
  }

  public String extractRole(String token) {
    return extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
  }

  public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
    final Claims claims = extractAllClaims(token);
    return claimsResolver.apply(claims);
  }

  public String generateToken(UserDetails userDetails) {
    return generateToken(new HashMap<>(), userDetails);
  }

  public String generateToken(
      Map<String, Object> extraClaims,
      UserDetails userDetails
  ) {
    Map<String, Object> claims = new HashMap<>(extraClaims);
    userDetails.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(auth -> auth != null && auth.startsWith("ROLE_"))
        .findFirst()
        .ifPresent(roleAuth -> claims.put(CLAIM_ROLE, roleAuth));
    return buildToken(claims, userDetails, jwtExpiration, TYPE_ACCESS);
  }

  public String generateRefreshToken(UserDetails userDetails) {
    return generateRefreshToken(userDetails, UUID.randomUUID().toString());
  }

  public String generateRefreshToken(
      UserDetails userDetails,
      String familyId
  ) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(CLAIM_SESSION_ID, familyId);
    return buildToken(claims, userDetails, refreshExpiration, TYPE_REFRESH);
  }

  private String buildToken(
      Map<String, Object> extraClaims,
      UserDetails userDetails,
      long expiration,
      String tokenType
  ) {
    return Jwts
        .builder()
        .claims(extraClaims)
        .subject(userDetails.getUsername())
        .id(UUID.randomUUID().toString())
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + expiration))
        .claim(CLAIM_TOKEN_TYPE, tokenType)
        .signWith(getSignInKey(), Jwts.SIG.HS256)
        .compact();
  }

  public Claims parseValidTokenClaims(String token, String expectedType) {
    Claims claims = extractAllClaims(token);
    String actualType = claims.get(CLAIM_TOKEN_TYPE, String.class);
    if (!expectedType.equals(actualType)) {
      throw new JwtException("Unexpected token_type claim");
    }
    if (claims.getExpiration() == null || !claims.getExpiration().after(new Date())) {
      throw new JwtException("Token has expired");
    }
    return claims;
  }

  public boolean isTokenValid(String token, UserDetails userDetails, String expectedType) {
    Claims claims = parseValidTokenClaims(token, expectedType);
    return userDetails.getUsername().equals(claims.getSubject())
        && userDetails.isEnabled();
  }

  public Date extractExpiration(String token) {
    return extractClaim(token, Claims::getExpiration);
  }

  private Claims extractAllClaims(String token) {
    return Jwts
        .parser()
        .verifyWith(getSignInKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  private SecretKey getSignInKey() {
    byte[] keyBytes = Decoders.BASE64.decode(secretKey);
    if (keyBytes.length < 32) {
      throw new IllegalStateException("JWT secret key must be at least 256 bits (32 bytes)");
    }
    return new SecretKeySpec(keyBytes, "HmacSHA256");
  }
}
