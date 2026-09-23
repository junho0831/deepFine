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
}
