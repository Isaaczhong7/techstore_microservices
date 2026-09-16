package com.techstore.order.dto;

import com.techstore.order.entity.PaymentStatus;
import com.techstore.order.entity.ReservationStatus;
import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelPaymentResponse {
    private PaymentStatus status;

    private UUID paymentId;

    private UUID orderId;

    private UUID reservationId;
}
