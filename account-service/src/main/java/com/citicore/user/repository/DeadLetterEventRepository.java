package com.citicore.user.repository;

import com.citicore.user.entity.DeadLetterEvent;
import com.citicore.user.entity.DLQStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, Long> {

    List<DeadLetterEvent> findByStatus(DLQStatus status);

    List<DeadLetterEvent> findByTopic(String topic);
}