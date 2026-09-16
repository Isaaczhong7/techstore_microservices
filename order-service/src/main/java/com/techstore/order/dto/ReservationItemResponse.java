package com.techstore.order.dto;


import com.techstore.order.entity.ReservationItemStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ReservationItemResponse {
    private Long productId;
    private Long quantity;
    private ReservationItemStatus status;
}
