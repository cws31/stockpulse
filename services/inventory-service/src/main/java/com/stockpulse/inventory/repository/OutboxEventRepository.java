package com.stockpulse.inventory.repository;

import com.stockpulse.inventory.domain.OutboxStatus;
import com.stockpulse.inventory.entity.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status, Pageable pageable);
}
