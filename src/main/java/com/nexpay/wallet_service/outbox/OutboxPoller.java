package com.nexpay.wallet_service.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexpay.wallet_service.model.OutboxEvent;
//import com.nexpay.wallet_service.model.OutboxStatus;
import com.nexpay.wallet_service.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final int BATCH_SIZE = 10;

    // ─────────────────────────────────────────
    // POLL EVERY 5 SECONDS
    // ─────────────────────────────────────────
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void poll() {

        List<OutboxEvent> pendingEvents = outboxRepository
                .findPendingEventsWithLockAndLimit(BATCH_SIZE);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.info("outbox poller found {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            process(event);
        }
    }

    // ─────────────────────────────────────────
    // PROCESS SINGLE EVENT
    // ─────────────────────────────────────────
 
    private void process(OutboxEvent event) {
        try {
            // serialize payload to JSON string
            String payload = objectMapper.writeValueAsString(event.getPayload());

            // publish to Kafka topic matching event type
            // wallet.debited → topic: wallet.debited
            // wallet.credited → topic: wallet.credited
            kafkaTemplate.send(event.getEventType(), event.getWalletId().toString(), payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("failed to publish event id: {} type: {} error: {}",
                                    event.getId(), event.getEventType(), ex.getMessage());
                            outboxRepository.markAsFailed(event.getId());
                        } else {
                            log.info("published event id: {} type: {} topic: {}",
                                    event.getId(), event.getEventType(),
                                    result.getRecordMetadata().topic());
                            outboxRepository.markAsPublished(event.getId(), Instant.now());
                        }
                    });

        } catch (Exception ex) {
            log.error("error processing outbox event id: {} error: {}",
                    event.getId(), ex.getMessage());
            outboxRepository.markAsFailed(event.getId());
        }
    }
}