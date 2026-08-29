package com.citicore.account.repository;

import com.citicore.account.entity.AccountOutboxEvent;
import com.citicore.account.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountOutboxRepository extends JpaRepository<AccountOutboxEvent, Long> {

    List<AccountOutboxEvent> findByStatus(OutboxStatus status);
}