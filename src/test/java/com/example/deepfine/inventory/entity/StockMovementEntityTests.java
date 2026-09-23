package com.example.deepfine.inventory.entity;

import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.exception.InventoryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockMovementEntityTests {
    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    @DisplayName("입고 이력은 양수 변경량을 기록한다")
    void receiptRecordsPositiveAmount(long amount) {
        var movement = StockMovementEntity.receipt(1L, amount);

        assertThat(movement)
                .hasFieldOrPropertyWithValue("inventoryId", 1L)
                .hasFieldOrPropertyWithValue("type", StockMovementEntity.Type.RECEIPT)
                .hasFieldOrPropertyWithValue("quantityDelta", amount);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    @DisplayName("출고 이력은 양수 입력을 음수 변경량으로 기록한다")
    void shipmentRecordsNegativeAmount(long amount) {
        var movement = StockMovementEntity.shipment(1L, amount);

        assertThat(movement)
                .hasFieldOrPropertyWithValue("inventoryId", 1L)
                .hasFieldOrPropertyWithValue("type", StockMovementEntity.Type.SHIPMENT)
                .hasFieldOrPropertyWithValue("quantityDelta", -amount);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("입고 이력은 0 이하 수량을 거부한다")
    void receiptRejectsNonPositiveAmount(long amount) {
        assertThatThrownBy(() -> StockMovementEntity.receipt(1L, amount))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INVALID_REQUEST));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("출고 이력은 부호 변환 전에 0 이하 수량을 거부한다")
    void shipmentRejectsNonPositiveAmount(long amount) {
        assertThatThrownBy(() -> StockMovementEntity.shipment(1L, amount))
                .isInstanceOfSatisfying(InventoryException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(InventoryErrorCode.INVALID_REQUEST));
    }
}
