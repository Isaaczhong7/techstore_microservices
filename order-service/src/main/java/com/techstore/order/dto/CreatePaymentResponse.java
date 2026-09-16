package com.techstore.order.dto;

import com.techstore.order.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentResponse {
    private UUID paymentId;
    private UUID orderId;
    private UUID reservationId;
    private PaymentStatus status;
    private UUID PaymentMethodId;
}
