package com.techstore.inventory.exception;

public class InvalidCancellationException extends RuntimeException{
    public InvalidCancellationException(String message){
        super(message);
    }
}
