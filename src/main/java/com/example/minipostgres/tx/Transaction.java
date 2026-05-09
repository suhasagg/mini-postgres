package com.example.minipostgres.tx;

import java.util.*;

public final class Transaction {
    private final long id;
    private final List<Runnable> undoActions = new ArrayList<>();
    private boolean active = true;

    public Transaction(long id) { this.id = id; }

    public long id() { return id; }
    public boolean active() { return active; }

    public void addUndo(Runnable action) { undoActions.add(action); }

    public void rollback() {
        ListIterator<Runnable> it = undoActions.listIterator(undoActions.size());
        while (it.hasPrevious()) it.previous().run();
        active = false;
    }

    public void commit() { active = false; }
}
