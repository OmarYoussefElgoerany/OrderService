package com.screen.orders.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface TokenRepository extends JpaRepository<Token, Integer> {

  @EntityGraph(attributePaths = "user")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Token t where t.tokenHash = :tokenHash and t.tokenType = :type")
  Optional<Token> findLockedByTokenHashAndType(@Param("tokenHash") String tokenHash, @Param("type") TokenType type);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Token> findByUser_IdAndTokenType(Integer userId, TokenType tokenType);

  @EntityGraph(attributePaths = "user")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from Token t join t.user u where u.email = :email and t.tokenType = :type")
  Optional<Token> findLockedByUserEmailAndType(@Param("email") String email, @Param("type") TokenType type);

  @EntityGraph(attributePaths = "user")
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select token from Token token where token.id = :id")
  Optional<Token> findLockedById(@Param("id") Integer id);

}
