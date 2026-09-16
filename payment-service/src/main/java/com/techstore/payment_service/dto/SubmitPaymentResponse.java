package com.techstore.payment_service.dto;

import com.techstore.payment_service.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitPaymentResponse {
    private UUID paymentId;

    private PaymentStatus status;

    private UUID orderId;

    private UUID reservationId;
}
