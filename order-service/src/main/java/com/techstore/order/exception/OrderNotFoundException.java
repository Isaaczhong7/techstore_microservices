package com.techstore.order.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException{
    public OrderNotFoundException(UUID reservationId) {
        super("Order not found: " + reservationId);
    }
}
