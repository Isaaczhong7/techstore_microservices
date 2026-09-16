package com.techstore.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateReservationRequest {
    private UUID orderId;
    private LocalDateTime expiresAt;
    private List<ReservationItemRequest> items;
}
