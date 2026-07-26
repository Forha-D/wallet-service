package com.nexpay.wallet_service.exception;

public class WalletNotFoundException extends RuntimeException {

    public WalletNotFoundException(String message) {
        super(message);
    }

    public WalletNotFoundException(String field, Object value) {
        super("wallet not found with " + field + ": " + value);
    }
}