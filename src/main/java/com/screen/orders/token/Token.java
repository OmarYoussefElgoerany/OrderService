package com.screen.orders.token;

import com.screen.orders.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "token",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_token_user_type",
        columnNames = {"user_id", "token_type"}
    )
)
public class Token {

  @Id
  @GeneratedValue
  private Integer id;

  @Column(name = "token", unique = true, nullable = false, length = 64)
  private String tokenHash;

  @Column(name = "family_id", nullable = false, length = 36)
  private String familyId;

  @Column(name = "previous_token_hash", length = 64)
  private String previousTokenHash;

  @Column(name = "rotated_at")
  private Instant rotatedAt;

  @Builder.Default
  @Enumerated(EnumType.STRING)
  @Column(name = "token_type", nullable = false)
  private TokenType tokenType = TokenType.REFRESH;

  private boolean revoked;

  private boolean expired;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;
}
