package com.nexpay.wallet_service.exception;

public class DuplicateTransactionException extends RuntimeException {

    public DuplicateTransactionException(String idempotencyKey) {
        super("transaction already exists with idempotency key: " + idempotencyKey);
    }
}