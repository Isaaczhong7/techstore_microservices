package com.techstore.order.dto;

import com.techstore.order.entity.OrderItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {

    private Long productId;

    private Long quantity;

    private BigDecimal unitPrice;

    private OrderItemStatus status;
}