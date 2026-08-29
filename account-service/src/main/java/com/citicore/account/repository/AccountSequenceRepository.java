package com.citicore.account.repository;

import com.citicore.account.entity.AccountSequence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface AccountSequenceRepository extends JpaRepository<AccountSequence, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountSequence> findById(Long id);
}


