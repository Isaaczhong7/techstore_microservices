package com.techstore.inventory.repository;

import com.techstore.inventory.entity.InventoryReservationEntity;
import com.techstore.inventory.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservationEntity, UUID> {
    List<InventoryReservationEntity>
    findByStatusInAndExpiresAtBefore(
            Collection<ReservationStatus> statuses,
            LocalDateTime time
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM InventoryReservationEntity r
        WHERE r.id = :id
    """)
    Optional<InventoryReservationEntity> findByIdForUpdate(
            @Param("id") UUID id
    );
}
