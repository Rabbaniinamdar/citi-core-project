package com.citicore.user.repository;

import com.citicore.user.entity.AccountOutboxEvent;
import com.citicore.user.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountOutboxRepository extends JpaRepository<AccountOutboxEvent, Long> {

    List<AccountOutboxEvent> findByStatus(OutboxStatus status);
}