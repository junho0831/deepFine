package com.example.deepfine;

import com.example.deepfine.inventory.service.InventoryService;
import com.example.deepfine.inventory.exception.InventoryException;
import com.example.deepfine.inventory.exception.InventoryErrorCode;
import com.example.deepfine.inventory.dto.ReceiveRequest;
import com.example.deepfine.inventory.dto.ShipRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryIntegrationTests {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort int port;
    @Autowired InventoryService inventory;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE stock_movement, inventory, product RESTART IDENTITY");
    }

    @Test
    void receiveShipAndReadThroughHttp() throws Exception {
        var received = request("POST", "/api/products/receipts", "{\"name\":\" 상품 A \",\"quantity\":10}");
        assertThat(received.statusCode()).isEqualTo(200);
        var product = mapper.readTree(received.body());
        long id = product.get("id").asLong();
        assertThat(product.get("name").asText()).isEqualTo("상품 A");
        assertThat(product.get("quantity").asLong()).isEqualTo(10);
        var again = request("POST", "/api/products/receipts", "{\"name\":\"상품 A\",\"quantity\":2}");
        assertThat(mapper.readTree(again.body()).get("id").asLong()).isEqualTo(id);
        var shipped = request("POST", "/api/products/" + id + "/shipments", "{\"quantity\":12}");
        assertThat(shipped.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(shipped.body()).get("quantity").asLong()).isZero();
        assertError(request("POST", "/api/products/" + id + "/shipments", "{\"quantity\":1}"),
                409, "INSUFFICIENT_STOCK");
        var read = request("GET", "/api/products/" + id, null);
        assertThat(read.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(read.body()).get("quantity").asLong()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "null", "{", "{\"name\":\" \",\"quantity\":1}",
            "{\"name\":\"A\",\"quantity\":0}", "{\"name\":\"A\",\"quantity\":-1}",
            "{\"name\":\"A\",\"quantity\":null}", "{\"name\":\"A\",\"quantity\":1.5}",
            "{\"name\":\"A\",\"quantity\":9223372036854775808}",
            "{\"name\":\"A\",\"quantity\":1,\"typo\":1}"
    })
    void rejectsInvalidReceipts(String body) throws Exception {
        assertError(request("POST", "/api/products/receipts", body), 400, "INVALID_REQUEST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isZero();
    }

    @Test
    void readsCurrentStockThroughHttp() throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", 10L)).id();
        var response = request("GET", "/api/products/" + id, null);
        assertThat(response.statusCode()).isEqualTo(200);
        var product = mapper.readTree(response.body());
        assertThat(product.get("id").asLong()).isEqualTo(id);
        assertThat(product.get("name").asText()).isEqualTo("A");
        assertThat(product.get("quantity").asLong()).isEqualTo(10);
    }

    @Test
    void rejectsInvalidQueryIdsAndMissingProducts() throws Exception {
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            assertError(request("GET", "/api/products/" + id, null), 400, "INVALID_REQUEST");
        }
        assertError(request("GET", "/api/products/999", null), 404, "PRODUCT_NOT_FOUND");
    }

    @Test
    void rejectsLongReceiptName() throws Exception {
        assertError(request("POST", "/api/products/receipts",
                "{\"name\":\"" + "A".repeat(101) + "\",\"quantity\":1}"), 400, "INVALID_REQUEST");
    }

    @Test
    void rejectsInvalidShipmentIdsAndMissingProducts() throws Exception {
        for (String id : List.of("0", "-1", "abc", "9223372036854775808")) {
            assertError(request("POST", "/api/products/" + id + "/shipments", "{\"quantity\":1}"),
                    400, "INVALID_REQUEST");
        }
        assertError(request("POST", "/api/products/999/shipments", "{\"quantity\":1}"),
                404, "PRODUCT_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "{\"quantity\":0}", "{\"quantity\":-1}", "{\"quantity\":1.5}"})
    void rejectsInvalidShipments(String body) throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", 10L)).id();
        assertError(request("POST", "/api/products/" + id + "/shipments", body), 400, "INVALID_REQUEST");
        assertThat(inventory.get(id).quantity()).isEqualTo(10);
    }

    @Test
    void rejectsOverflowWithoutChangingStock() throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", Long.MAX_VALUE)).id();
        assertError(request("POST", "/api/products/receipts", "{\"name\":\"A\",\"quantity\":1}"),
                409, "STOCK_LIMIT_EXCEEDED");
        assertThat(inventory.get(id).quantity()).isEqualTo(Long.MAX_VALUE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement", Long.class)).isEqualTo(1);
    }

    @Test
    void concurrentNewProductReceiptsCreateOneRowAndLoseNoUpdates() throws Exception {
        concurrently(80, i -> inventory.receive(new ReceiveRequest("동시 등록", 1L)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM inventory", Long.class)).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE type = 'RECEIPT'", Long.class)).isEqualTo(80);
        assertThat(jdbc.queryForObject("SELECT sum(quantity_delta) FROM stock_movement", Long.class)).isEqualTo(80);
    }

    @Test
    void concurrentShipmentsNeverOversell() throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", 20L)).id();
        var success = new java.util.concurrent.atomic.AtomicInteger();
        var conflicts = new java.util.concurrent.atomic.AtomicInteger();
        concurrently(80, i -> {
            try {
                inventory.ship(id, new ShipRequest(1L));
                success.incrementAndGet();
            } catch (InventoryException e) {
                assertThat(e.errorCode()).isEqualTo(InventoryErrorCode.INSUFFICIENT_STOCK);
                conflicts.incrementAndGet();
            }
        });
        assertThat(success.get()).isEqualTo(20);
        assertThat(conflicts.get()).isEqualTo(60);
        assertThat(inventory.get(id).quantity()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE type = 'SHIPMENT'", Long.class)).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT sum(quantity_delta) FROM stock_movement", Long.class)).isZero();
    }

    @Test
    void concurrentReceiptsAndShipmentsPreserveExactQuantity() throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", 100L)).id();
        concurrently(100, i -> {
            if (i % 2 == 0) inventory.receive(new ReceiveRequest("A", 2L));
            else inventory.ship(id, new ShipRequest(1L));
        });
        assertThat(inventory.get(id).quantity()).isEqualTo(150);
        assertThat(jdbc.queryForObject("SELECT sum(quantity_delta) FROM stock_movement", Long.class)).isEqualTo(150);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement", Long.class)).isEqualTo(101);
    }

    @Test
    void ddlRejectsNegativeStockAndDuplicateNames() {
        inventory.receive(new ReceiveRequest("A", 1L));
        assertThatThrownBy(() -> jdbc.update("UPDATE inventory SET quantity = -1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product(name, sku) VALUES ('A', 'OTHER-SKU')"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void failedMovementInsertRollsBackStockAndNewProduct() {
        jdbc.execute("ALTER TABLE stock_movement ADD CONSTRAINT test_reject_seven CHECK (quantity_delta <> 7)");
        try {
            long id = inventory.receive(new ReceiveRequest("existing", 10L)).id();
            assertThatThrownBy(() -> inventory.receive(new ReceiveRequest("existing", 7L)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(inventory.get(id).quantity()).isEqualTo(10);
            assertThatThrownBy(() -> inventory.receive(new ReceiveRequest("new", 7L)))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement", Long.class)).isEqualTo(1);
        } finally {
            jdbc.execute("ALTER TABLE stock_movement DROP CONSTRAINT test_reject_seven");
        }
    }

    @Test
    void stockIsSeparatedByWarehouseAndConstraintsProtectReferences() {
        long productId = inventory.receive(new ReceiveRequest("A", 10L)).id();
        long warehouseId = jdbc.queryForObject(
                "INSERT INTO warehouse(code, name) VALUES ('SECOND', '두 번째 창고') RETURNING id", Long.class);
        try {
            long stockId = jdbc.queryForObject(
                    "INSERT INTO inventory(product_id, warehouse_id, quantity) VALUES (?, ?, 0) RETURNING id",
                    Long.class, productId, warehouseId);
            inventory.ship(productId, new ShipRequest(3L));
            assertThat(inventory.get(productId).quantity()).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT quantity FROM inventory WHERE id = ?", Long.class, stockId)).isZero();
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO inventory(product_id, warehouse_id) VALUES (?, ?)", productId, warehouseId))
                    .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM product WHERE id = ?", productId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO stock_movement(inventory_id, type, quantity_delta) VALUES (?, 'RECEIPT', -1)", stockId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO stock_movement(inventory_id, type, quantity_delta) VALUES (?, 'SHIPMENT', 0)", stockId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {
            jdbc.update("DELETE FROM inventory WHERE warehouse_id = ?", warehouseId);
            jdbc.update("DELETE FROM warehouse WHERE id = ?", warehouseId);
        }
    }

    @Test
    @DisplayName("여러 상품의 입출고 이력이 각 상품 재고에 연결된다")
    void movementsReferenceTheCorrectProductStock() {
        long firstId = inventory.receive(new ReceiveRequest("상품 A", 10L)).id();
        long secondId = inventory.receive(new ReceiveRequest("상품 B", 20L)).id();

        inventory.ship(firstId, new ShipRequest(3L));

        var history = jdbc.query("""
                SELECT i.product_id, w.code, m.type, m.quantity_delta
                FROM stock_movement m
                JOIN inventory i ON i.id = m.inventory_id
                JOIN warehouse w ON w.id = i.warehouse_id
                ORDER BY m.id
                """, (row, index) -> tuple(row.getLong("product_id"), row.getString("code"),
                        row.getString("type"), row.getLong("quantity_delta")));
        assertThat(history).containsExactly(
                tuple(firstId, "DEFAULT", "RECEIPT", 10L),
                tuple(secondId, "DEFAULT", "RECEIPT", 20L),
                tuple(firstId, "DEFAULT", "SHIPMENT", -3L));
        assertThat(inventory.get(firstId).quantity()).isEqualTo(7);
        assertThat(inventory.get(secondId).quantity()).isEqualTo(20);
    }

    @Test
    @DisplayName("출고 이력 저장 실패 시 500을 반환하고 재고와 이력을 유지한다")
    void failedShipmentHistoryInsertRollsBackStockThroughHttp() throws Exception {
        long id = inventory.receive(new ReceiveRequest("상품 A", 10L)).id();
        jdbc.execute("ALTER TABLE stock_movement ADD CONSTRAINT test_reject_shipment CHECK (type <> 'SHIPMENT')");
        try {
            assertError(request("POST", "/api/products/" + id + "/shipments", "{\"quantity\":3}"),
                    500, "INTERNAL_SERVER_ERROR");

            assertThat(inventory.get(id).quantity()).isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sum(quantity_delta) FROM stock_movement", Long.class)).isEqualTo(10);
        } finally {
            jdbc.execute("ALTER TABLE stock_movement DROP CONSTRAINT test_reject_shipment");
        }
    }

    @Test
    @DisplayName("커밋 시 재고 UPDATE가 실패하면 먼저 INSERT한 이력도 롤백된다")
    void failedStockUpdateRollsBackInsertedHistoryThroughHttp() throws Exception {
        long id = inventory.receive(new ReceiveRequest("상품 A", 10L)).id();
        jdbc.execute("ALTER TABLE inventory ADD CONSTRAINT test_reject_seven_stock CHECK (quantity <> 7)");
        try {
            assertError(request("POST", "/api/products/" + id + "/shipments", "{\"quantity\":3}"),
                    500, "INTERNAL_SERVER_ERROR");

            assertThat(inventory.get(id).quantity()).isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT sum(quantity_delta) FROM stock_movement", Long.class)).isEqualTo(10);
        } finally {
            jdbc.execute("ALTER TABLE inventory DROP CONSTRAINT test_reject_seven_stock");
        }
    }

    @Test
    @DisplayName("경로 ID 검증 실패도 필드와 원인을 제공한다")
    void invalidPathIdIncludesFieldMessage() throws Exception {
        var response = request("GET", "/api/products/0", null);
        assertError(response, 400, "INVALID_REQUEST");
        var errors = mapper.readTree(response.body()).get("errors");
        assertThat(errors.get(0).get("field").asText()).isEqualTo("id");
        assertThat(errors.get(0).get("message").asText()).isEqualTo("상품 ID는 양수여야 합니다.");
    }

    @Test
    @DisplayName("상품 이력은 다른 상품을 제외하고 동일 시각에도 ID 역순으로 페이징한다")
    void movementHistoryIsScopedAndPaged() throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", 10L)).id();
        inventory.receive(new ReceiveRequest("B", 20L));
        inventory.ship(id, new ShipRequest(3L));
        inventory.receive(new ReceiveRequest("A", 2L));
        jdbc.update("UPDATE stock_movement SET created_at = '2026-01-01T00:00:00Z'");

        var response = request("GET", "/api/products/" + id + "/movements?page=0&size=2", null);
        assertThat(response.statusCode()).isEqualTo(200);
        var page = mapper.readTree(response.body());
        assertThat(page.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("items").size()).isEqualTo(2);
        assertThat(page.get("items").get(0).get("quantityDelta").asLong()).isEqualTo(2);
        assertThat(page.get("items").get(1).get("type").asText()).isEqualTo("SHIPMENT");
        assertThat(page.get("items").get(1).get("quantityDelta").asLong()).isEqualTo(-3);
        var second = mapper.readTree(request("GET", "/api/products/" + id + "/movements?page=1&size=2", null).body());
        assertThat(second.get("items").size()).isEqualTo(1);
        assertThat(second.get("items").get(0).get("quantityDelta").asLong()).isEqualTo(10);
        var beyond = mapper.readTree(request("GET", "/api/products/" + id + "/movements?page=2&size=2", null).body());
        assertThat(beyond.get("items").size()).isZero();
        assertThat(beyond.get("totalElements").asLong()).isEqualTo(3);
    }

    @Test
    @DisplayName("이력이 없으면 빈 페이지, 상품 재고가 없으면 404를 반환한다")
    void historyHandlesEmptyAndMissingStock() throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", 1L)).id();
        jdbc.update("DELETE FROM stock_movement");
        var response = request("GET", "/api/products/" + id + "/movements", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("items").size()).isZero();
        assertError(request("GET", "/api/products/999/movements", null), 404, "PRODUCT_NOT_FOUND");
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "size=2147483648"})
    void historyRejectsInvalidPagination(String query) throws Exception {
        assertError(request("GET", "/api/products/1/movements?" + query, null), 400, "INVALID_REQUEST");
    }

    private void concurrently(int count, IntConsumer action) throws Exception {
        int workers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                final int index = i;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                    action.accept(index);
                    return null;
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertError(HttpResponse<String> response, int status, String code) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        var error = mapper.readTree(response.body());
        assertThat(error.get("status").asInt()).isEqualTo(status);
        assertThat(error.get("code").asText()).isEqualTo(code);
    }
}
