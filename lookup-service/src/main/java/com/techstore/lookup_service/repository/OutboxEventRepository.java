package com.techstore.lookup_service.repository;


import com.techstore.lookup_service.entity.OutboxEventEntity;
import com.techstore.lookup_service.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity>
    findTop100ByStatusOrderByCreatedAtAsc(
            OutboxStatus status
    );
}
