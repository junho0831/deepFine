package com.example.deepfine;

import com.example.deepfine.inventory.InventoryService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void ddlRejectsNegativeStockAndDuplicateNames() {
        jdbc.update("INSERT INTO product(name, quantity) VALUES ('A', 1)");
        assertThatThrownBy(() -> jdbc.update("UPDATE product SET quantity = -1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product(name, quantity) VALUES ('A', 1)"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
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
