package com.nexpay.wallet_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexpay.wallet_service.dto.DebitRequest;
import com.nexpay.wallet_service.exception.InsufficientBalanceException;
import com.nexpay.wallet_service.exception.WalletNotFoundException;
import com.nexpay.wallet_service.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final WalletService walletService;
    private final ObjectMapper  objectMapper;

    // ─────────────────────────────────────────
    // CONSUME order.created
    // ─────────────────────────────────────────
    @KafkaListener(
            topics          = "order.created",
            groupId         = "wallet-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        log.info("received order.created event offset: {} partition: {}",
                record.offset(), record.partition());

        try {
            // STEP 1: deserialize payload
            Map<String, Object> payload = objectMapper.readValue(
                    record.value(),
                    objectMapper.getTypeFactory()
                            .constructMapType(Map.class, String.class, Object.class)
            );

            // STEP 2: extract fields
            UUID   userId         = UUID.fromString((String) payload.get("user_id"));
            UUID   orderId        = UUID.fromString((String) payload.get("order_id"));
            BigDecimal totalAmount = new BigDecimal(payload.get("total_amount").toString());
            String idempotencyKey  = "order-debit-" + orderId.toString();

            log.info("processing debit for userId: {} orderId: {} amount: {}",
                    userId, orderId, totalAmount);

            // STEP 3: build debit request
            DebitRequest debitRequest = new DebitRequest();
            debitRequest.setAmount(totalAmount);
            debitRequest.setIdempotencyKey(idempotencyKey);
            debitRequest.setReferenceId(orderId);
            debitRequest.setReferenceType("ORDER");
            debitRequest.setDescription("payment for order: " + orderId);

            // STEP 4: debit wallet
            // debit() internally:
            // - checks idempotency
            // - locks wallet row
            // - checks balance
            // - debits balance
            // - writes outbox event wallet.debited
            walletService.debit(userId, debitRequest);

            // STEP 5: acknowledge — tell Kafka this message is processed
            acknowledgment.acknowledge();

            log.info("order.created processed successfully orderId: {}", orderId);

        } catch (InsufficientBalanceException ex) {
            // insufficient balance → publish wallet.debit.failed
            // order service will cancel the order
            log.warn("insufficient balance for order event: {}", ex.getMessage());
            handleDebitFailed(record, ex.getMessage(), acknowledgment);

        } catch (WalletNotFoundException ex) {
            // wallet not found → publish wallet.debit.failed
            log.warn("wallet not found for order event: {}", ex.getMessage());
            handleDebitFailed(record, ex.getMessage(), acknowledgment);

        } catch (Exception ex) {
            // unexpected error → do NOT acknowledge → Kafka will redeliver
            log.error("unexpected error processing order.created: {}", ex.getMessage(), ex);
            // do not call acknowledgment.acknowledge() → triggers retry
        }
    }

    // ─────────────────────────────────────────
    // HANDLE DEBIT FAILED
    // ─────────────────────────────────────────
    private void handleDebitFailed(
            ConsumerRecord<String, String> record,
            String reason,
            Acknowledgment acknowledgment
    ) {
        try {
            Map<String, Object> payload = objectMapper.readValue(
                    record.value(),
                    objectMapper.getTypeFactory()
                            .constructMapType(Map.class, String.class, Object.class)
            );

            String orderId = (String) payload.get("order_id");
            String userId  = (String) payload.get("user_id");

            log.warn("debit failed for orderId: {} userId: {} reason: {}",
                    orderId, userId, reason);

            // write wallet.debit.failed outbox event
            // outbox poller will publish it to Kafka
            // Order Service will consume it and cancel the order
            walletService.writeDebitFailedEvent(orderId, userId, reason);

            // acknowledge — message is handled, don't redeliver
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("error handling debit failed: {}", ex.getMessage(), ex);
        }
    }
}
