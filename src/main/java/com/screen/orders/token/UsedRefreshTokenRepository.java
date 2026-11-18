package com.screen.orders.token;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UsedRefreshTokenRepository extends JpaRepository<UsedRefreshToken, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select used from UsedRefreshToken used where used.tokenHash = :tokenHash")
  Optional<UsedRefreshToken> findLockedByTokenHash(@Param("tokenHash") String tokenHash);

  @Modifying
  @Query("delete from UsedRefreshToken used where used.expiresAt <= :now")
  int deleteExpiredBefore(@Param("now") Instant now);

  @Modifying
  @Query("delete from UsedRefreshToken used where used.session.id = :sessionId")
  int deleteAllBySessionId(@Param("sessionId") Integer sessionId);
}
