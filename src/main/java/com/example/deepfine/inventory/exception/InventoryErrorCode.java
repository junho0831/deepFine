package com.example.deepfine.inventory.exception;

public enum InventoryErrorCode {
    PRODUCT_NOT_FOUND("상품을 찾을 수 없습니다."),
    INSUFFICIENT_STOCK("출고 가능한 재고가 부족합니다."),
    STOCK_LIMIT_EXCEEDED("입고 후 재고가 허용 가능한 최대 수량을 초과합니다."),
    IDEMPOTENCY_CONFLICT("동일한 요청 키를 다른 입출고 요청에 사용할 수 없습니다."),
    INVALID_IDEMPOTENCY_KEY("요청 키는 영문, 숫자, 점, 밑줄, 콜론, 하이픈으로 구성한 1~128자여야 합니다."),
    INVALID_REQUEST("수량은 양수여야 합니다.");

    private final String message;

    InventoryErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
