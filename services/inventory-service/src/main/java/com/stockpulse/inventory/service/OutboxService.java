package com.stockpulse.inventory.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.inventory.entity.OutboxEvent;
import com.stockpulse.inventory.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void add(String aggregateId, String eventType, String correlationId, Object payload) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", eventType);
        envelope.put("eventVersion", 1);
        envelope.put("aggregateId", aggregateId);
        envelope.put("occurredAt", Instant.now());
        envelope.put("correlationId", correlationId);
        envelope.put("payload", payload);

        try {
            String json = objectMapper.writeValueAsString(envelope);
            repository.save(new OutboxEvent(eventId, aggregateId, eventType, correlationId, json));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize inventory event", exception);
        }
    }
}
