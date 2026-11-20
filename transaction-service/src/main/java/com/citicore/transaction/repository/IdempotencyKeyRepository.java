package com.citicore.transaction.repository;

import com.citicore.transaction.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKey, Long> {

    Optional<IdempotencyKey> findByAuthUserIdAndIdempotencyKey(
            Long authUserId, String idempotencyKey
    );

    @Deprecated
    Optional<IdempotencyKey> findByIdempotencyKey(
            String idempotencyKey
    );
}