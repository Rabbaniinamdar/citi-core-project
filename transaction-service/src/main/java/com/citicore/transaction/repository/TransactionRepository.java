package com.citicore.transaction.repository;

import com.citicore.transaction.entity.Transaction;
import com.citicore.transaction.entity.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTxnRef(String txnRef);

    Page<Transaction> findByAuthUserId(Long authUserId, Pageable pageable);

    Page<Transaction> findByAuthUserIdAndStatus(
            Long authUserId,
            TransactionStatus status,
            Pageable pageable);

    /**
     * Calculates total amount transferred by a user today.
     * Used by TransactionLimitValidator to enforce daily limits.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.authUserId = :userId
              AND t.createdAt >= :start
              AND t.createdAt < :end
              AND t.status != 'FAILED'
              AND t.status != 'REVERSED'
            """)
    BigDecimal getTodayTotal(
            @Param("userId") Long userId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
}