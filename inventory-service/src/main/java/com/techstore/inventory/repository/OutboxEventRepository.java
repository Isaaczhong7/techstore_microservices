package com.techstore.inventory.repository;

import com.techstore.inventory.entity.OutboxEventEntity;
import com.techstore.inventory.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity>
    findTop100ByStatusOrderByCreatedAtAsc(
            OutboxStatus status
    );
}
