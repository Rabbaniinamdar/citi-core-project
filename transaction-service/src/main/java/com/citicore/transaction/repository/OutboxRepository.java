package com.citicore.transaction.repository;

import com.citicore.transaction.entity.OutboxEvent;
import com.citicore.transaction.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findByStatus(OutboxStatus status);

    List<OutboxEvent> findByStatusIn(
            List<OutboxStatus> statuses
    );
}