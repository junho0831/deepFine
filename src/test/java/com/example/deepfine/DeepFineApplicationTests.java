package com.example.deepfine;

import com.example.deepfine.inventory.service.InventoryService;
import com.example.deepfine.inventory.exception.InventoryException;
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
class DeepFineApplicationTests {
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
        jdbc.execute("TRUNCATE TABLE product RESTART IDENTITY");
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
        long id = jdbc.queryForObject("INSERT INTO product(name, quantity) VALUES ('A', 10) RETURNING id", Long.class);
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
    }

    @Test
    void concurrentNewProductReceiptsCreateOneRowAndLoseNoUpdates() throws Exception {
        concurrently(80, i -> inventory.receive(new ReceiveRequest("동시 등록", 1L)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM product", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM product", Long.class)).isEqualTo(80);
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
                assertThat(e.code()).isEqualTo("INSUFFICIENT_STOCK");
                conflicts.incrementAndGet();
            }
        });
        assertThat(success.get()).isEqualTo(20);
        assertThat(conflicts.get()).isEqualTo(60);
        assertThat(inventory.get(id).quantity()).isZero();
    }

    @Test
    void concurrentReceiptsAndShipmentsPreserveExactQuantity() throws Exception {
        long id = inventory.receive(new ReceiveRequest("A", 100L)).id();
        concurrently(100, i -> {
            if (i % 2 == 0) inventory.receive(new ReceiveRequest("A", 2L));
            else inventory.ship(id, new ShipRequest(1L));
        });
        assertThat(inventory.get(id).quantity()).isEqualTo(150);
    }

    @Test
    void ddlRejectsNegativeStockAndDuplicateNames() {
        jdbc.update("INSERT INTO product(name, quantity) VALUES ('A', 1)");
        assertThatThrownBy(() -> jdbc.update("UPDATE product SET quantity = -1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product(name, quantity) VALUES ('A', 1)"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
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
