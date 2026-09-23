package com.example.deepfine.inventory.entity;

import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.exception.InventoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryEntityTests {
    @Test
    @DisplayName("입고 수량이 기존 재고에 누적된다")
    void receiveAddsToExistingStock() {
        InventoryEntity inventory = new InventoryEntity();
        inventory.receive(10);

        inventory.receive(5);

        assertThat(inventory.getQuantity()).isEqualTo(15);
    }

    @Test
    @DisplayName("출고 수량만큼 재고가 감소한다")
    void shipSubtractsFromStock() {
        InventoryEntity inventory = new InventoryEntity();
        inventory.receive(10);

        inventory.ship(3);

        assertThat(inventory.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("재고 전량을 출고하면 수량이 0이 된다")
    void shipAllStockLeavesZero() {
        InventoryEntity inventory = new InventoryEntity();
        inventory.receive(10);

        inventory.ship(10);

        assertThat(inventory.getQuantity()).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 10})
    @DisplayName("재고보다 많은 출고는 거부하고 기존 수량을 유지한다")
    void insufficientStockLeavesQuantityUnchanged(long initialQuantity) {
        InventoryEntity inventory = new InventoryEntity();
        if (initialQuantity > 0) inventory.receive(initialQuantity);

        assertThatThrownBy(() -> inventory.ship(initialQuantity + 1))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INSUFFICIENT_STOCK));
        assertThat(inventory.getQuantity()).isEqualTo(initialQuantity);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("0 이하의 입고 수량은 거부하고 기존 재고를 유지한다")
    void invalidReceiptLeavesQuantityUnchanged(long amount) {
        InventoryEntity inventory = new InventoryEntity();
        inventory.receive(10);

        assertThatThrownBy(() -> inventory.receive(amount))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INVALID_REQUEST));
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("0 이하의 출고 수량은 거부하고 기존 재고를 유지한다")
    void invalidShipmentLeavesQuantityUnchanged(long amount) {
        InventoryEntity inventory = new InventoryEntity();
        inventory.receive(10);

        assertThatThrownBy(() -> inventory.ship(amount))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INVALID_REQUEST));
        assertThat(inventory.getQuantity()).isEqualTo(10);
    }

    @Test
    @DisplayName("입고 결과가 long 최댓값과 같으면 허용한다")
    void receiveUpToLimitSucceeds() {
        InventoryEntity inventory = new InventoryEntity();
        inventory.receive(Long.MAX_VALUE - 1);

        inventory.receive(1);

        assertThat(inventory.getQuantity()).isEqualTo(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(longs = {2, Long.MAX_VALUE})
    @DisplayName("입고 결과가 long 범위를 넘으면 거부하고 기존 재고를 유지한다")
    void overflowLeavesQuantityUnchanged(long amount) {
        InventoryEntity inventory = new InventoryEntity();
        inventory.receive(Long.MAX_VALUE - 1);

        assertThatThrownBy(() -> inventory.receive(amount))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.STOCK_LIMIT_EXCEEDED));
        assertThat(inventory.getQuantity()).isEqualTo(Long.MAX_VALUE - 1);
    }

    @Test
    @DisplayName("long 최댓값 수량도 입고 후 전량 출고할 수 있다")
    void receiveAndShipMaximumAmount() {
        InventoryEntity inventory = new InventoryEntity();

        inventory.receive(Long.MAX_VALUE);
        assertThat(inventory.getQuantity()).isEqualTo(Long.MAX_VALUE);
        inventory.ship(Long.MAX_VALUE);

        assertThat(inventory.getQuantity()).isZero();
    }
}
