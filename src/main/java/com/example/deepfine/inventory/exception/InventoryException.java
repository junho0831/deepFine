package com.example.deepfine.inventory.exception;

public class InventoryException extends RuntimeException {
    private final InventoryErrorCode errorCode;

    public InventoryException(InventoryErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }

    public InventoryErrorCode errorCode() {
        return errorCode;
    }
}
