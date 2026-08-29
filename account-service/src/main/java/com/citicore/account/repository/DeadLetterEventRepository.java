package com.citicore.account.repository;

import com.citicore.account.entity.DeadLetterEvent;
import com.citicore.account.entity.DLQStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {

    List<DeadLetterEvent> findByStatus(DLQStatus status);

    List<DeadLetterEvent> findByTopic(String topic);
}