package com.techstore.order.dto;

import lombok.*;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CancelPaymentRequest {
    private UUID paymentId;
    private UUID orderId;

}
