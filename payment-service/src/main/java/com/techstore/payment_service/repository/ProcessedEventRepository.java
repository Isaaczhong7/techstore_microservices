package com.techstore.payment_service.repository;

import com.techstore.payment_service.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {

    @Modifying
    @Query(
            value = """
                INSERT INTO processed_event (
                    event_id,
                    event_type,
                    processed_at
                )
                VALUES (
                    :eventId,
                    :eventType,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (event_id) DO NOTHING
                """,
            nativeQuery = true
    )

    int tryMarkProcessed(
            @Param("eventId") UUID eventId,
            @Param("eventType") String eventType
    );
}