package com.techstore.order.dto;

import com.techstore.order.entity.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReservationStatusResponse {
    private UUID orderId;
    private UUID paymentId;
    private UUID reservationId;
    private OrderStatus status;
    private LocalDateTime expiresAt;
    private Long expiresInSeconds;
}
