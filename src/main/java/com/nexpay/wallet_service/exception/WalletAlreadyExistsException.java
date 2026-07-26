package com.nexpay.wallet_service.exception;

import java.util.UUID;

public class WalletAlreadyExistsException extends RuntimeException {

    public WalletAlreadyExistsException(UUID userId) {
        super("wallet already exists for user: " + userId);
    }
}