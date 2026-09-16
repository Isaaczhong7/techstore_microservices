package com.techstore.order.repository;

import com.techstore.order.entity.OrderEntity;
import com.techstore.order.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT o
        FROM OrderEntity o
        WHERE o.id = :id
    """)
    Optional<OrderEntity> findByIdForUpdate(
            @Param("id") UUID id
    );

}