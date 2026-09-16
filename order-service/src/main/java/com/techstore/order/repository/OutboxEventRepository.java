package com.techstore.order.repository;

import com.techstore.order.entity.OutboxEventEntity;
import com.techstore.order.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity>
    findTop100ByStatusOrderByCreatedAtAsc(
            OutboxStatus status
    );
}
