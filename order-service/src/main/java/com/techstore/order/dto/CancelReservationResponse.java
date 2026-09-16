package com.techstore.order.dto;

import com.techstore.order.entity.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelReservationResponse {
    private UUID orderId;
    private UUID reservationId;
    private ReservationStatus status;
}
