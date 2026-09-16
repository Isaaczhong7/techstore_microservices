package com.techstore.payment_service.repository;

import com.techstore.payment_service.entity.PaymentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM PaymentEntity o
        WHERE o.id = :id
    """)
    Optional<PaymentEntity> findByIdForUpdate(
            @Param("id") UUID id
    );

}
