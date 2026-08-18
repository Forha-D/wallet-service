package com.nexpay.wallet_service.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexpay.wallet_service.dto.CreditRequest;
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
public class TopupEventConsumer {

    private final WalletService walletService;
    private final ObjectMapper  objectMapper;

    // ─────────────────────────────────────────
    // CONSUME topup.confirmed
    // emitted by Payment Service after gateway confirms
    // ─────────────────────────────────────────
    @KafkaListener(
            topics           = "topup.confirmed",
            groupId          = "wallet-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onTopupConfirmed(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        log.info("received topup.confirmed event offset: {} partition: {}",
                record.offset(), record.partition());

        try {
            // STEP 1: deserialize payload
            Map<String, Object> payload = objectMapper.readValue(
                    record.value(),
                    objectMapper.getTypeFactory()
                            .constructMapType(Map.class, String.class, Object.class)
            );

            // STEP 2: extract fields
            UUID       userId     = UUID.fromString((String) payload.get("user_id"));
            UUID       paymentId  = UUID.fromString((String) payload.get("payment_id"));
            BigDecimal amount     = new BigDecimal(payload.get("amount").toString());
            String     gateway    = (String) payload.get("gateway");
            String     gatewayRef = (String) payload.get("gateway_ref");

            // STEP 3: idempotency key — payment_id is unique per top-up
            String idempotencyKey = "topup-credit-" + paymentId.toString();

            log.info("processing top-up credit userId: {} paymentId: {} amount: {} gateway: {}",
                    userId, paymentId, amount, gateway);

            // STEP 4: build credit request
            CreditRequest creditRequest = new CreditRequest();
            creditRequest.setAmount(amount);
            creditRequest.setIdempotencyKey(idempotencyKey);
            creditRequest.setReferenceId(paymentId);
            creditRequest.setReferenceType("TOPUP");
            creditRequest.setDescription("top-up via " + gateway + " ref: " + gatewayRef);

            // STEP 5: credit wallet
            // credit() internally:
            // - checks idempotency → safe on Kafka redeliver
            // - locks wallet row
            // - credits balance
            // - writes outbox event wallet.credited
            walletService.credit(userId, creditRequest);

            // STEP 6: acknowledge
            acknowledgment.acknowledge();

            log.info("topup.confirmed processed successfully userId: {} amount: {} gateway: {}",
                    userId, amount, gateway);

        } catch (WalletNotFoundException ex) {
            // wallet not found → acknowledge and log
            // do not retry — wallet genuinely does not exist
            log.error("wallet not found for topup event: {}", ex.getMessage());
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            // unexpected error → do NOT acknowledge → Kafka redelivers
            log.error("unexpected error processing topup.confirmed: {}", ex.getMessage(), ex);
        }
    }
}