package com.atrum.agrum.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BiometricKeyRepository extends JpaRepository<BiometricKey, String> {

    @Transactional
    @Modifying
    @Query("DELETE FROM BiometricKey k WHERE k.username = :username")
    void deleteAllByUsername(String username);

    /**
     * Uses up the challenge in one statement, so two logins racing with the same signature can't both
     * pass. 1 when it was still there, 0 when another login (or a newer challenge) took it.
     */
    @Transactional
    @Modifying
    @Query("UPDATE BiometricKey k SET k.challenge = null, k.challengeExpiresAt = null "
            + "WHERE k.id = :id AND k.challenge = :challenge")
    int consumeChallenge(String id, String challenge);
}
