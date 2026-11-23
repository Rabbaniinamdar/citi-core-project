package com.citicor.auth.repository;

import com.citicor.auth.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByEmail(String email);

    @Modifying
    @Transactional
    @Query("UPDATE LoginAttempt la SET la.failedCount = 0, la.lockedUntil = null, la.updatedAt = :now WHERE la.email = :email")
    void resetFailedCount(@Param("email") String email, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM LoginAttempt la WHERE la.lockedUntil IS NOT NULL AND la.lockedUntil < :now")
    void deleteExpiredLockouts(@Param("now") LocalDateTime now);
}