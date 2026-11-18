package com.screen.orders.token;

import com.screen.orders.user.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "token")
public class Token {

  @Id
  @GeneratedValue
  public Integer id;

  @Column(name = "token", unique = true, nullable = false)
  public String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "token_type")
  public TokenType tokenType = TokenType.BEARER;

  public boolean revoked;

  public boolean expired;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  public User user;
}
