package com.screen.orders.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

  @Value("${application.security.jwt.secret-key}")
  private String secretKey;
  @Value("${application.security.jwt.expiration}")
  private long jwtExpiration;
  @Value("${application.security.jwt.refresh-token.expiration}")
  private long refreshExpiration;

  public String extractUsername(String token) {
    return extractClaim(token, Claims::getSubject);
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
    return buildToken(extraClaims, userDetails, jwtExpiration, "access");
  }

  public String generateRefreshToken(
      UserDetails userDetails
  ) {
    return buildToken(new HashMap<>(), userDetails, refreshExpiration, "refresh");
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
            .claim("token_type", tokenType)
            .signWith(getSignInKey())
            .compact();
  }

  public boolean isTokenValid(String token, UserDetails userDetails, String expectedType) {
    Claims claims = extractAllClaims(token);
    return userDetails.getUsername().equals(claims.getSubject())
        && userDetails.isEnabled()
        && expectedType.equals(claims.get("token_type", String.class))
        && claims.getExpiration().after(new Date());
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
    return Keys.hmacShaKeyFor(keyBytes);
  }
}
