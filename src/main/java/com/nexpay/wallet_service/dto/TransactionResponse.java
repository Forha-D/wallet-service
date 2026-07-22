package com.nexpay.wallet_service.dto;

import com.nexpay.wallet_service.model.TransactionStatus;
import com.nexpay.wallet_service.model.TransactionType;
import com.nexpay.wallet_service.model.WalletTransaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID walletId;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String idempotencyKey;
    private UUID referenceId;
    private String referenceType;
    private String description;
    private Map<String, Object> metadata;
    private TransactionStatus status;
    private Instant createdAt;

    public static TransactionResponse from(WalletTransaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .walletId(tx.getWallet().getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .idempotencyKey(tx.getIdempotencyKey())
                .referenceId(tx.getReferenceId())
                .referenceType(tx.getReferenceType())
                .description(tx.getDescription())
                .metadata(tx.getMetadata())
                .status(tx.getStatus())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}