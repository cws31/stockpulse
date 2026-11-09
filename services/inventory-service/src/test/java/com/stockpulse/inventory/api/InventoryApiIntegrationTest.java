package com.stockpulse.inventory.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class InventoryApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("stockpulse.outbox.poll-interval-ms", () -> "60000");
        registry.add("stockpulse.reservation.expiry-scan-interval-ms", () -> "60000");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void publishesOpenApiSpecificationWithServiceMetadata() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("StockPulse Inventory Service API"))
                .andExpect(jsonPath("$.paths['/api/inventory/reservations'].post.summary")
                        .value("Reserve inventory for an order"));
    }

    @Test
    void malformedJsonReturnsReadableBadRequest() throws Exception {
        mockMvc.perform(post("/api/inventory/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Request body is missing or contains invalid JSON or field values"));
    }

    @Test
    void createsProductAndReturnsInitialStock() throws Exception {
        UUID productId = createProduct("API-CREATE", 12);

        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.totalStock").value(12))
                .andExpect(jsonPath("$.availableStock").value(12))
                .andExpect(jsonPath("$.reservedStock").value(0));
    }

    @Test
    void rejectsInvalidProductPayloadWithReadableValidationMessage() throws Exception {
        mockMvc.perform(post("/api/inventory/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":" ","name":"Laptop","initialStock":-1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void duplicateSkuReturnsConflict() throws Exception {
        String sku = "DUPLICATE-" + UUID.randomUUID();
        createProduct(sku, 1);
        mockMvc.perform(post("/api/inventory/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A product with this SKU already exists"));
    }

    @Test
    void reservationConfirmConsumesStock() throws Exception {
        UUID productId = createProduct("API-CONFIRM", 10);
        JsonNode reservation = reserve(productId, "ORDER-API-CONFIRM", 3,
                "IDEMPOTENCY-API-CONFIRM");
        String reservationId = reservation.get("reservationId").asText();

        mockMvc.perform(post("/api/inventory/reservations/{id}/confirm", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(7))
                .andExpect(jsonPath("$.availableStock").value(7))
                .andExpect(jsonPath("$.reservedStock").value(0));
    }

    @Test
    void reservationReleaseReturnsStockToAvailable() throws Exception {
        UUID productId = createProduct("API-RELEASE", 10);
        JsonNode reservation = reserve(productId, "ORDER-API-RELEASE", 4,
                "IDEMPOTENCY-API-RELEASE");
        String reservationId = reservation.get("reservationId").asText();

        mockMvc.perform(post("/api/inventory/reservations/{id}/release", reservationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));

        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(10))
                .andExpect(jsonPath("$.availableStock").value(10))
                .andExpect(jsonPath("$.reservedStock").value(0));
    }

    @Test
    void insufficientStockReturnsConflictAndDoesNotMutateInventory() throws Exception {
        UUID productId = createProduct("API-INSUFFICIENT", 2);
        mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationPayload(productId, "ORDER-API-INSUFFICIENT", 3,
                                "IDEMPOTENCY-API-INSUFFICIENT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Insufficient available stock"));

        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(2))
                .andExpect(jsonPath("$.reservedStock").value(0));
    }

    @Test
    void idempotentRetryReturnsSameReservationAndDeductsStockOnce() throws Exception {
        UUID productId = createProduct("API-IDEMPOTENT", 8);
        String body = reservationPayload(productId, "ORDER-API-IDEMPOTENT", 3,
                "IDEMPOTENCY-API-IDEMPOTENT");

        JsonNode first = objectMapper.readTree(mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode second = objectMapper.readTree(mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertEquals(first.get("reservationId").asText(), second.get("reservationId").asText());
        assertEquals("Existing reservation returned", second.get("message").asText());
        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(5))
                .andExpect(jsonPath("$.reservedStock").value(3));
    }

    @Test
    void simultaneousRetriesWithSameIdempotencyKeyReturnOneReservation() throws Exception {
        UUID productId = createProduct("API-CONCURRENT-IDEMPOTENCY", 10);
        int requestCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<CompletableFuture<MvcResult>> requests = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                requests.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return mockMvc.perform(post("/api/inventory/reservations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(reservationPayload(productId, "ORDER-SAME-KEY", 3, "SAME-CONCURRENT-KEY")))
                                .andReturn();
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }, executor));
            }
            List<MvcResult> results = requests.stream().map(CompletableFuture::join).toList();
            assertTrue(results.stream().allMatch(result -> result.getResponse().getStatus() == 201),
                    results.stream().map(result -> result.getResponse().getStatus()).toList().toString());
            List<JsonNode> responses = results.stream().map(result -> {
                try { return objectMapper.readTree(result.getResponse().getContentAsString()); }
                catch (Exception exception) { throw new RuntimeException(exception); }
            }).toList();
            assertEquals(responses.get(0).get("reservationId").asText(),
                    responses.get(1).get("reservationId").asText());
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableStock").value(7))
                .andExpect(jsonPath("$.reservedStock").value(3));
    }

    @Test
    void idempotencyKeyCannotBeReusedForDifferentPayload() throws Exception {
        UUID productId = createProduct("API-IDEMPOTENCY-CONFLICT", 8);
        reserve(productId, "ORDER-API-IDEMPOTENCY-CONFLICT", 2, "IDEMPOTENCY-CONFLICT");

        mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationPayload(productId, "ORDER-API-IDEMPOTENCY-CONFLICT", 3,
                                "IDEMPOTENCY-CONFLICT")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "Idempotency key was already used for a different request"));
    }

    @Test
    void stockAdjustmentRejectsRemovalOfReservedQuantity() throws Exception {
        UUID productId = createProduct("API-ADJUSTMENT", 5);
        reserve(productId, "ORDER-API-ADJUSTMENT", 3, "IDEMPOTENCY-API-ADJUSTMENT");

        mockMvc.perform(post("/api/inventory/stock/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":-3,"reason":"Incorrect count"}
                                """.formatted(productId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(5))
                .andExpect(jsonPath("$.availableStock").value(2))
                .andExpect(jsonPath("$.reservedStock").value(3));
    }

    @Test
    void unknownProductAndReservationReturnNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();
        mockMvc.perform(get("/api/inventory/products/{id}/stock", unknownId))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/inventory/reservations/{id}/confirm", unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    void concurrentReservationsNeverOversell() throws Exception {
        UUID productId = createProduct("API-CONCURRENCY", 10);
        int requestCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<CompletableFuture<Integer>> requests = new ArrayList<>();
            for (int i = 0; i < requestCount; i++) {
                final int requestNumber = i;
                requests.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return mockMvc.perform(post("/api/inventory/reservations")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(reservationPayload(productId,
                                                "ORDER-CONCURRENT-" + requestNumber, 3,
                                                "IDEMPOTENCY-CONCURRENT-" + requestNumber)))
                                .andReturn().getResponse().getStatus();
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                }, executor));
            }

            List<Integer> statuses = requests.stream().map(CompletableFuture::join).toList();
            assertEquals(3, statuses.stream().filter(status -> status == 201).count(), statuses.toString());
            assertEquals(2, statuses.stream().filter(status -> status == 409).count(), statuses.toString());
        } finally {
            executor.shutdownNow();
        }

        mockMvc.perform(get("/api/inventory/products/{id}/stock", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStock").value(10))
                .andExpect(jsonPath("$.availableStock").value(1))
                .andExpect(jsonPath("$.reservedStock").value(9));
    }

    private UUID createProduct(String skuPrefix, long initialStock) throws Exception {
        String sku = skuPrefix + "-" + UUID.randomUUID();
        String response = mockMvc.perform(post("/api/inventory/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productPayload(sku, initialStock)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("productId").asText());
    }

    private JsonNode reserve(UUID productId, String orderId, long quantity, String key) throws Exception {
        String response = mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reservationPayload(productId, orderId, quantity, key)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String productPayload(String sku, long initialStock) {
        return "{\"sku\":\"" + sku + "\",\"name\":\"Integration Test Product\",\"initialStock\":" + initialStock + "}";
    }

    private String reservationPayload(UUID productId, String orderId, long quantity, String key) {
        return "{\"orderId\":\"" + orderId + "\",\"productId\":\"" + productId
                + "\",\"quantity\":" + quantity + ",\"idempotencyKey\":\"" + key + "\"}";
    }
}
