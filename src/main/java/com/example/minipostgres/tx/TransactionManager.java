package com.example.minipostgres.tx;

import java.util.concurrent.atomic.AtomicLong;

public final class TransactionManager {
    private final AtomicLong nextTx = new AtomicLong(1);

    public Transaction begin() {
        return new Transaction(nextTx.getAndIncrement());
    }
}
