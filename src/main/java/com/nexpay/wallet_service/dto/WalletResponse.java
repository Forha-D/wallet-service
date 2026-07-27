package com.nexpay.wallet_service.dto;

import com.nexpay.wallet_service.model.Wallet;
import com.nexpay.wallet_service.model.WalletStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {

    private UUID id;
    private UUID userId;
    private BigDecimal balance;
    private String currency;
    private WalletStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    public static WalletResponse from(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}