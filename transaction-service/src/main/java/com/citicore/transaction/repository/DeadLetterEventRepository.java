package com.citicore.transaction.repository;

import com.citicore.transaction.entity.DeadLetterEvent;
import com.citicore.transaction.entity.DLQStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {
    List<DeadLetterEvent> findByStatus(DLQStatus status);
    List<DeadLetterEvent> findByTopic(String topic);
}