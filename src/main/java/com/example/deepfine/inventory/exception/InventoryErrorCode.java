package com.example.deepfine.inventory.exception;

public enum InventoryErrorCode {
    PRODUCT_NOT_FOUND("상품을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK("출고 가능한 재고가 부족합니다."),
    STOCK_LIMIT_EXCEEDED("입고 후 재고가 허용 가능한 최대 수량을 초과합니다."),
    INVALID_REQUEST("수량은 양수여야 합니다.");

    private final String message;

    InventoryErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
