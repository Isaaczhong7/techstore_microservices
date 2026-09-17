package com.techstore.order.dto;

import com.techstore.order.entity.OrderStatus;
import com.techstore.order.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private UUID id;

    private UUID reservationId;

    private UUID paymentId;

    private UUID customerId;

    private String email;

    private CustomerType customerType;

    private OrderStatus status;

    private PaymentStatus paymentStatus;

    private BigDecimal totalAmount;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    private List<OrderItemResponse> items;
}
