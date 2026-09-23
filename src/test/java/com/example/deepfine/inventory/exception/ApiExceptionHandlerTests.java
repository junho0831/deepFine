package com.example.deepfine.inventory.exception;

import com.example.deepfine.inventory.controller.InventoryController;
import com.example.deepfine.inventory.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiExceptionHandlerTests {
    private InventoryService inventory;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        inventory = mock(InventoryService.class);
        mvc = MockMvcBuilders.standaloneSetup(new InventoryController(inventory))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("예상하지 못한 예외는 내부 정보를 노출하지 않고 500 응답을 반환한다")
    void unexpectedExceptionReturnsProblemWithoutInternalDetails() throws Exception {
        when(inventory.get(1L)).thenThrow(new IllegalStateException("민감한 내부 오류 정보"));

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value("서버 내부 오류가 발생했습니다."))
                .andExpect(content().string(not(containsString("민감한 내부 오류 정보"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }

    @Test
    @DisplayName("DB 예외는 전용 처리기를 통해 503 응답을 반환한다")
    void databaseExceptionKeepsSpecificHandler() throws Exception {
        when(inventory.get(1L)).thenThrow(new DataAccessResourceFailureException("DB 연결 실패"));

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("상품 없음 예외는 전용 처리기를 통해 404 응답을 반환한다")
    void inventoryExceptionKeepsSpecificHandler() throws Exception {
        when(inventory.get(1L)).thenThrow(new InventoryException(
                InventoryErrorCode.PRODUCT_NOT_FOUND));

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }
    @Test
    @DisplayName("DB 제약 위반은 내부 오류로 처리하고 상세 SQL을 노출하지 않는다")
    void unexpectedConstraintViolationReturns500() throws Exception {
        when(inventory.get(1L)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("민감한 SQL"));
        mvc.perform(get("/api/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(content().string(not(containsString("민감한 SQL"))));
    }

    @Test
    @DisplayName("잠금 획득 실패는 일시적 DB 장애로 처리한다")
    void lockFailureReturns503() throws Exception {
        when(inventory.get(1L)).thenThrow(new org.springframework.dao.CannotAcquireLockException("lock timeout"));
        mvc.perform(get("/api/products/1"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("입력 검증 실패 응답은 필드별 원인을 제공한다")
    void invalidFieldsReturnFieldMessages() throws Exception {
        mvc.perform(post("/api/products/receipts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \" ,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')].message").value(
                        org.hamcrest.Matchers.hasItem("상품명은 필수입니다.")))
                .andExpect(jsonPath("$.errors[?(@.field == 'quantity')].message").value(
                        org.hamcrest.Matchers.hasItem("수량은 양수여야 합니다.")));
        org.mockito.Mockito.verifyNoInteractions(inventory);
    }

    @Test
    @DisplayName("깨진 JSON은 내부 파싱 정보를 노출하지 않고 400으로 응답한다")
    void malformedJsonReturnsGeneric400() throws Exception {
        mvc.perform(post("/api/products/receipts").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(content().string(not(containsString("Exception"))));
        org.mockito.Mockito.verifyNoInteractions(inventory);
    }

}
