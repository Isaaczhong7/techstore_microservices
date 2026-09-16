package com.techstore.payment_service.repository;


import com.techstore.payment_service.entity.OutboxEventEntity;
import com.techstore.payment_service.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity>
    findTop100ByStatusOrderByCreatedAtAsc(
            OutboxStatus status
    );
}
