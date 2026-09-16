package com.techstore.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {
    private CustomerType customerType;

    // Only used for members
    private UUID customerId;

    // Useful for guest checkout / order confirmation
    private String email;

    private List<OrderItemRequest> items;
}
