package com.example.minipostgres.engine;

import com.example.minipostgres.tx.Transaction;

public final class Session {
    private Transaction transaction;

    public boolean inTransaction() { return transaction != null && transaction.active(); }
    public Transaction transaction() { return transaction; }
    public void setTransaction(Transaction transaction) { this.transaction = transaction; }
    public void clearTransaction() { this.transaction = null; }
}
