package com.nexpay.wallet_service.exception;

import java.util.UUID;

public class WalletFrozenException extends RuntimeException {

    public WalletFrozenException(UUID userId) {
        super("wallet is frozen for user: " + userId);
    }
}