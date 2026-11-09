package com.stockpulse.inventory.service;

import com.stockpulse.inventory.domain.OutboxStatus;
import com.stockpulse.inventory.entity.OutboxEvent;
import com.stockpulse.inventory.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           @Value("${stockpulse.events.topic:inventory-events}") String topic) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${stockpulse.outbox.poll-interval-ms:1000}")
    public void publishPendingEvents() {
        var pending = repository.findByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING, PageRequest.of(0, 50));

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
                markPublished(event);
            } catch (Exception exception) {
                recordFailure(event);
                log.warn("Outbox event {} could not be published; it will be retried",
                        event.getId(), exception);
            }
        }
    }

    protected void markPublished(OutboxEvent event) {
        repository.findById(event.getId()).ifPresent(current -> {
            current.markPublished();
            repository.save(current);
        });
    }

    protected void recordFailure(OutboxEvent event) {
        repository.findById(event.getId()).ifPresent(current -> {
            current.incrementAttempts();
            repository.save(current);
        });
    }
}
