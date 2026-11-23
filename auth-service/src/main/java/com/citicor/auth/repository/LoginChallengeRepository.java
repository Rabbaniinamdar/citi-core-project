package com.citicor.auth.repository;

import com.citicor.auth.entity.LoginChallenge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoginChallengeRepository extends JpaRepository<LoginChallenge, Long> {

    Optional<LoginChallenge> findByChallengeId(String challengeId);

    List<LoginChallenge> findByUserIdAndConsumedFalseAndPurpose(Long userId, String purpose);

    @Modifying
    @Transactional
    @Query("DELETE FROM LoginChallenge lc WHERE lc.expiresAt < :now")
    int deleteExpired(LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM LoginChallenge lc WHERE lc.userId = :userId AND lc.purpose = :purpose AND lc.consumed = false")
    int deleteUnconsumedByUserAndPurpose(Long userId, String purpose);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT lc
        FROM LoginChallenge lc
        WHERE lc.challengeId = :challengeId
        """)
    Optional<LoginChallenge> findByChallengeIdForUpdate(
            @Param("challengeId") String challengeId
);
}