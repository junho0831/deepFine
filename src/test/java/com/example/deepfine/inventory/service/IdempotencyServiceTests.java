package com.example.deepfine.inventory.service;

import com.example.deepfine.inventory.dto.ProductResponse;
import com.example.deepfine.inventory.entity.IdempotencyRequestEntity;
import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.repository.IdempotencyRequestRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTests {
    @Mock IdempotencyRequestRepository requests;
    private IdempotencyService service;
    private final ProductResponse response = new ProductResponse(1L, "A", 10L);

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(requests);
    }

    @Test
    @DisplayName("키가 없으면 저장소를 사용하지 않고 기존 작업을 실행한다")
    void missingKeyRunsAction() {
        assertThat(service.execute(null, "receipt", () -> response)).isEqualTo(response);
        verifyNoInteractions(requests);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "with space", "한글", "a/b"})
    @DisplayName("허용되지 않은 키는 작업 실행 전에 거부한다")
    void invalidKeyDoesNotRunAction(String key) {
        assertThatThrownBy(() -> service.execute(key, "receipt", () -> {
            throw new AssertionError("실행되면 안 됩니다.");
        })).isInstanceOfSatisfying(InventoryException.class,
                e -> assertThat(e.errorCode()).isEqualTo(InventoryErrorCode.INVALID_IDEMPOTENCY_KEY));
        verifyNoInteractions(requests);
    }

    @Test
    void oversizedKeyIsRejected() {
        assertThatThrownBy(() -> service.execute("a".repeat(129), "receipt", () -> response))
                .isInstanceOf(InventoryException.class);
        verifyNoInteractions(requests);
    }

    @Test
    @DisplayName("같은 키와 내용은 최초 결과를 반환하고 작업을 다시 실행하지 않는다")
    void sameContentReplaysResponse() {
        storedRequest();
        AtomicInteger calls = new AtomicInteger();
        assertThat(service.execute("key", "receipt", () -> {
            calls.incrementAndGet();
            return response;
        })).isEqualTo(response);
        assertThat(service.execute("key", "receipt", () -> {
            calls.incrementAndGet();
            return new ProductResponse(1L, "A", 999L);
        })).isEqualTo(response);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 키의 다른 내용은 작업 실행 없이 충돌로 처리한다")
    void differentContentConflicts() {
        storedRequest();
        service.execute("key", "receipt", () -> response);
        assertThatThrownBy(() -> service.execute("key", "shipment", () -> {
            throw new AssertionError("실행되면 안 됩니다.");
        })).isInstanceOfSatisfying(InventoryException.class,
                e -> assertThat(e.errorCode()).isEqualTo(InventoryErrorCode.IDEMPOTENCY_CONFLICT));
    }

    private void storedRequest() {
        AtomicReference<IdempotencyRequestEntity> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            if (stored.get() == null) {
                var entity = BeanUtils.instantiateClass(IdempotencyRequestEntity.class);
                ReflectionTestUtils.setField(entity, "requestKey", invocation.getArgument(0));
                ReflectionTestUtils.setField(entity, "requestHash", invocation.getArgument(1));
                stored.set(entity);
            }
            return null;
        }).when(requests).insertIfAbsent(eq("key"), anyString());
        when(requests.findForUpdate("key")).thenAnswer(ignored -> Optional.of(stored.get()));
    }
}
