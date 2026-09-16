package com.techstore.inventory.exception;

public class InventoryConsistencyException
        extends RuntimeException {

    public InventoryConsistencyException(String message) {
        super(message);
    }
}