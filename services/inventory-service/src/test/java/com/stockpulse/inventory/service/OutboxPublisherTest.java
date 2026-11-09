package com.stockpulse.inventory.service;

import com.stockpulse.inventory.domain.OutboxStatus;
import com.stockpulse.inventory.entity.OutboxEvent;
import com.stockpulse.inventory.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxEventRepository repository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishesPendingEventAndMarksItPublished() {
        OutboxEvent event = event();
        when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send("inventory-events", "ORDER-1", "{\"event\":\"created\"}"))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher().publishPendingEvents();

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        verify(repository).save(event);
    }

    @Test
    void failedPublishLeavesEventPendingAndIncrementsRetryAttempts() {
        OutboxEvent event = event();
        when(repository.findByStatusOrderByCreatedAtAsc(eq(OutboxStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(repository.findById(event.getId())).thenReturn(Optional.of(event));
        when(kafkaTemplate.send("inventory-events", "ORDER-1", "{\"event\":\"created\"}"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        assertDoesNotThrow(() -> publisher().publishPendingEvents());

        assertAll(
                () -> assertEquals(OutboxStatus.PENDING, event.getStatus()),
                () -> assertEquals(1, event.getAttempts()),
                () -> verify(repository).save(event));
    }

    private OutboxPublisher publisher() {
        return new OutboxPublisher(repository, kafkaTemplate, "inventory-events");
    }

    private OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), "ORDER-1", "OrderCreated", null,
                "{\"event\":\"created\"}");
    }
}
