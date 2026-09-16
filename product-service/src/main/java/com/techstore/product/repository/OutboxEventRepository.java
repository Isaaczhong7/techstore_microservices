package com.techstore.product.repository;


import com.techstore.product.entity.OutboxEventEntity;
import com.techstore.product.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
    List<OutboxEventEntity>
    findTop100ByStatusOrderByCreatedAtAsc(
            OutboxStatus status
    );
}
