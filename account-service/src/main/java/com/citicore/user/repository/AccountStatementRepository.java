package com.citicore.user.repository;

import com.citicore.user.entity.AccountStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountStatementRepository extends JpaRepository<AccountStatement, Long> {

    /**
     * Idempotency check — used in debit() and credit() before processing.
     * If a statement with this txnRef already exists, the operation was already
     * processed. Safe to skip — prevents double debit/credit on Kafka redelivery.
     */
    boolean existsByTxnRef(String txnRef);

    /** Last 10 transactions for mini-statement endpoint. */
    List<AccountStatement> findTop10ByAccountNumberOrderByCreatedAtDesc(String accountNumber);

    /** Full paginated statement. */
    Page<AccountStatement> findByAccountNumberOrderByCreatedAtDesc(
            String accountNumber, Pageable pageable);
}