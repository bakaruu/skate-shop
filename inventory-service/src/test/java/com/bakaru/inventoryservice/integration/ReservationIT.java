package com.bakaru.inventoryservice.integration;

import com.bakaru.inventoryservice.dto.InventoryRequest;
import com.bakaru.common.dto.ReservationLine;
import com.bakaru.inventoryservice.repository.InventoryRepository;
import com.bakaru.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises stock reservation against a real Postgres instance (no mocked repository), proving
 * the reserve/release endpoints and the @Transactional rollback-on-partial-failure behavior
 * actually hold up against a real database, not just Mockito's in-memory state.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class ReservationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedInventory() {
        // the Postgres container (and its data) is shared across all test methods in this class,
        // so reset the table before each test rather than relying on it being empty.
        inventoryRepository.deleteAll();
        inventoryService.createInventory(InventoryRequest.builder().productId(100L).quantity(5).build());
        inventoryService.createInventory(InventoryRequest.builder().productId(101L).quantity(1).build());
    }

    @Test
    void reserve_withSufficientStock_incrementsReservedInDatabase() throws Exception {
        List<ReservationLine> lines = List.of(new ReservationLine(100L, 3));

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isOk());

        assertThat(inventoryService.getByProductId(100L).getReserved()).isEqualTo(3);
        assertThat(inventoryService.getByProductId(100L).getAvailable()).isEqualTo(2);
    }

    @Test
    void reserve_withInsufficientStock_returns409() throws Exception {
        List<ReservationLine> lines = List.of(new ReservationLine(101L, 5));

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isConflict());

        assertThat(inventoryService.getByProductId(101L).getReserved()).isZero();
    }

    @Test
    void reserve_whenSecondLineInsufficient_rollsBackFirstLineReservation() throws Exception {
        List<ReservationLine> lines = List.of(
                new ReservationLine(100L, 3),
                new ReservationLine(101L, 5));

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isConflict());

        assertThat(inventoryService.getByProductId(100L).getReserved())
                .as("the whole batch is one @Transactional method, so the first line's reservation must roll back too")
                .isZero();
    }

    @Test
    void reserveThenRelease_returnsReservedToZero() throws Exception {
        List<ReservationLine> lines = List.of(new ReservationLine(100L, 3));

        mockMvc.perform(post("/api/inventory/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/inventory/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(lines)))
                .andExpect(status().isOk());

        assertThat(inventoryService.getByProductId(100L).getReserved()).isZero();
    }
}
