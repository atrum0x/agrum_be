package com.atrum.agrum.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    List<RefreshToken> findAllByUsername(String username);

    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.username = :username")
    void deleteAllByUsername(String username);

    /** Sessions that ran out without a logout; cleared at each login so the table doesn't grow. */
    @Transactional
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.username = :username AND t.expiryDate < :now")
    void deleteExpired(String username, Instant now);
}
