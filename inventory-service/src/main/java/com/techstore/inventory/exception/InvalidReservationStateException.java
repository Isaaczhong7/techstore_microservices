package com.techstore.inventory.exception;

public class InvalidReservationStateException extends RuntimeException {

    public InvalidReservationStateException(String message) {
            super(message);
    }
}
