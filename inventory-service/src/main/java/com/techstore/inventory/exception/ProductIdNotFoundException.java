package com.techstore.inventory.exception;

public class ProductIdNotFoundException extends RuntimeException{
    public ProductIdNotFoundException(String message){
        super(message);
    }
}
