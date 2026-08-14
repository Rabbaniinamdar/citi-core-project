package com.citicore.user.repository;

import com.citicore.user.entity.Account;
import com.citicore.user.entity.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findByAuthUserId(Long authUserId);

    Optional<Account> findByAuthUserIdAndIsDefaultTrue(Long authUserId);

    boolean existsByAuthUserIdAndAccountType(Long authUserId, AccountType accountType);

    /**
     * Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the account row.
     *
     * Used by debit() and credit() to prevent race conditions when two concurrent
     * transfers attempt to modify the same account simultaneously.
     *
     * Thread A: SELECT ... FOR UPDATE → gets lock, reads balance 5000
     * Thread B: SELECT ... FOR UPDATE → WAITS
     * Thread A: debit 3000, saves balance 2000, releases lock
     * Thread B: now gets lock, reads balance 2000 → correct ✅
     *
     * Without lock: both threads read 5000, both debit 3000 → balance goes negative 💀
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accNo")
    Optional<Account> findByAccountNumberWithLock(@Param("accNo") String accNo);
}