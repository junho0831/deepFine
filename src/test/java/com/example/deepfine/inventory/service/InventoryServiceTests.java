package com.example.deepfine.inventory.service;

import com.example.deepfine.inventory.dto.ProductResponse;
import com.example.deepfine.inventory.dto.ReceiveRequest;
import com.example.deepfine.inventory.dto.ShipRequest;
import com.example.deepfine.inventory.entity.InventoryEntity;
import com.example.deepfine.inventory.entity.ProductEntity;
import com.example.deepfine.inventory.entity.StockMovementEntity;
import com.example.deepfine.inventory.entity.WarehouseEntity;
import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.repository.InventoryRepository;
import com.example.deepfine.inventory.repository.ProductRepository;
import com.example.deepfine.inventory.repository.StockMovementRepository;
import com.example.deepfine.inventory.repository.WarehouseRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {
    @Mock ProductRepository products;
    @Mock InventoryRepository inventories;
    @Mock WarehouseRepository warehouses;
    @Mock StockMovementRepository movements;

    private InventoryService service;
    private ProductEntity product;
    private WarehouseEntity warehouse;

    @BeforeEach
    void setUp() {
        service = new InventoryService(products, inventories, warehouses, movements);
        product = BeanUtils.instantiateClass(ProductEntity.class);
        ReflectionTestUtils.setField(product, "id", 11L);
        ReflectionTestUtils.setField(product, "name", "상품 A");
        warehouse = BeanUtils.instantiateClass(WarehouseEntity.class);
        ReflectionTestUtils.setField(warehouse, "id", 22L);
    }

    @Test
    @DisplayName("조회한 기본 창고 재고를 응답으로 반환한다")
    void getReturnsStock() {
        defaultWarehouseExists();
        when(inventories.findStock(11L, 22L)).thenReturn(Optional.of(stock(10)));

        assertThat(service.get(11L)).isEqualTo(new ProductResponse(11L, "상품 A", 10L));
        verifyNoInteractions(movements);
    }

    @Test
    @DisplayName("조회 대상 재고가 없으면 상품 없음 오류를 반환한다")
    void getRejectsMissingStock() {
        defaultWarehouseExists();
        when(inventories.findStock(11L, 22L)).thenReturn(Optional.empty());

        assertError(() -> service.get(11L), InventoryErrorCode.PRODUCT_NOT_FOUND);
        verifyNoInteractions(movements);
    }

    @Test
    @DisplayName("기존 상품 입고는 재고를 늘리고 입고 이력을 저장한다")
    void receiveExistingStock() {
        InventoryEntity stock = existingStock(10);

        assertThat(service.receive(new ReceiveRequest("상품 A", 3L)))
                .isEqualTo(new ProductResponse(11L, "상품 A", 13L));
        assertThat(stock.getQuantity()).isEqualTo(13);
        verify(products, never()).insertIfAbsent(anyString(), anyString());
        verify(inventories, never()).insertIfAbsent(anyLong(), anyLong());
        assertSavedMovement(StockMovementEntity.Type.RECEIPT, 3L);
    }

    @Test
    @DisplayName("미등록 상품은 생성 후 다시 조회하고 최초 재고에 입고한다")
    void receiveCreatesProductAndStock() {
        defaultWarehouseExists();
        when(products.findByName("상품 A")).thenReturn(Optional.empty()).thenReturn(Optional.of(product));
        when(inventories.findStockForUpdate(11L, 22L)).thenReturn(Optional.empty()).thenReturn(Optional.of(stock(0)));

        assertThat(service.receive(new ReceiveRequest("상품 A", 5L)).quantity()).isEqualTo(5);
        verify(products).insertIfAbsent(eq("상품 A"), startsWith("AUTO-"));
        verify(products, times(2)).findByName("상품 A");
        verify(inventories).insertIfAbsent(11L, 22L);
        verify(inventories, times(2)).findStockForUpdate(11L, 22L);
        assertSavedMovement(StockMovementEntity.Type.RECEIPT, 5L);
    }

    @Test
    @DisplayName("상품 생성 후에도 조회되지 않으면 재고와 이력을 생성하지 않는다")
    void receiveRejectsMissingProductAfterInsert() {
        when(products.findByName("상품 A")).thenReturn(Optional.empty());

        assertError(() -> service.receive(new ReceiveRequest("상품 A", 5L)), InventoryErrorCode.PRODUCT_NOT_FOUND);
        verify(products).insertIfAbsent(eq("상품 A"), anyString());
        verifyNoInteractions(inventories, movements);
    }

    @Test
    @DisplayName("재고 생성 후에도 조회되지 않으면 이력을 저장하지 않는다")
    void receiveRejectsMissingStockAfterInsert() {
        defaultWarehouseExists();
        when(products.findByName("상품 A")).thenReturn(Optional.of(product));
        when(inventories.findStockForUpdate(11L, 22L)).thenReturn(Optional.empty());

        assertError(() -> service.receive(new ReceiveRequest("상품 A", 5L)), InventoryErrorCode.PRODUCT_NOT_FOUND);
        verify(inventories).insertIfAbsent(11L, 22L);
        verifyNoInteractions(movements);
    }

    @Test
    @DisplayName("입고 수량 상한 초과 시 재고를 유지하고 이력을 저장하지 않는다")
    void receiveRejectsOverflowWithoutHistory() {
        InventoryEntity stock = existingStock(Long.MAX_VALUE);

        assertError(() -> service.receive(new ReceiveRequest("상품 A", 1L)), InventoryErrorCode.STOCK_LIMIT_EXCEEDED);
        assertThat(stock.getQuantity()).isEqualTo(Long.MAX_VALUE);
        verifyNoInteractions(movements);
    }

    @Test
    @DisplayName("출고는 재고를 차감하고 음수 변경량 이력을 저장한다")
    void shipSavesNegativeMovement() {
        defaultWarehouseExists();
        when(inventories.findStockForUpdate(11L, 22L)).thenReturn(Optional.of(stock(10)));

        assertThat(service.ship(11L, new ShipRequest(3L)))
                .isEqualTo(new ProductResponse(11L, "상품 A", 7L));
        assertSavedMovement(StockMovementEntity.Type.SHIPMENT, -3L);
    }

    @Test
    @DisplayName("출고 재고 부족 시 수량을 유지하고 이력을 저장하지 않는다")
    void shipRejectsInsufficientStockWithoutHistory() {
        defaultWarehouseExists();
        InventoryEntity stock = stock(2);
        when(inventories.findStockForUpdate(11L, 22L)).thenReturn(Optional.of(stock));

        assertError(() -> service.ship(11L, new ShipRequest(3L)), InventoryErrorCode.INSUFFICIENT_STOCK);
        assertThat(stock.getQuantity()).isEqualTo(2);
        verifyNoInteractions(movements);
    }

    @Test
    @DisplayName("출고 대상이 없으면 상품 없음 오류를 반환하고 이력을 저장하지 않는다")
    void shipRejectsMissingStock() {
        defaultWarehouseExists();
        when(inventories.findStockForUpdate(11L, 22L)).thenReturn(Optional.empty());

        assertError(() -> service.ship(11L, new ShipRequest(3L)), InventoryErrorCode.PRODUCT_NOT_FOUND);
        verifyNoInteractions(movements);
    }

    @Test
    @DisplayName("기본 창고가 없으면 재고 조회를 진행하지 않는다")
    void getRejectsMissingDefaultWarehouse() {
        when(warehouses.findByCode("DEFAULT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(11L))
                .isInstanceOf(IllegalStateException.class).hasMessage("기본 창고가 없습니다.");
        verifyNoInteractions(inventories, movements);
    }

    private void defaultWarehouseExists() {
        when(warehouses.findByCode("DEFAULT")).thenReturn(Optional.of(warehouse));
    }

    private InventoryEntity existingStock(long quantity) {
        defaultWarehouseExists();
        when(products.findByName("상품 A")).thenReturn(Optional.of(product));
        InventoryEntity stock = stock(quantity);
        when(inventories.findStockForUpdate(11L, 22L)).thenReturn(Optional.of(stock));
        return stock;
    }

    // DB가 채우는 영속 상태만 구성하고 수량 변경에는 실제 엔티티를 사용한다.
    private InventoryEntity stock(long quantity) {
        InventoryEntity stock = BeanUtils.instantiateClass(InventoryEntity.class);
        ReflectionTestUtils.setField(stock, "id", 33L);
        ReflectionTestUtils.setField(stock, "product", product);
        ReflectionTestUtils.setField(stock, "warehouseId", 22L);
        ReflectionTestUtils.setField(stock, "quantity", quantity);
        return stock;
    }

    private void assertSavedMovement(StockMovementEntity.Type type, long delta) {
        var captor = ArgumentCaptor.forClass(StockMovementEntity.class);
        verify(movements).save(captor.capture());
        assertThat(captor.getValue())
                .hasFieldOrPropertyWithValue("inventoryId", 33L)
                .hasFieldOrPropertyWithValue("type", type)
                .hasFieldOrPropertyWithValue("quantityDelta", delta);
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, InventoryErrorCode code) {
        assertThatThrownBy(action).isInstanceOfSatisfying(InventoryException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(code));
    }
}
