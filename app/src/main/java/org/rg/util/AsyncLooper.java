package org.rg.util;

import java.util.concurrent.CompletableFuture;

public class AsyncLooper {

    private Boolean isAlive;
    private Runnable action;

    public AsyncLooper(Runnable action) {
        this.action = action;
    }

    public synchronized AsyncLooper activate() {
        if (isAlive == null) {
            isAlive = true;
        } else {
            throw new IllegalStateException("Could not activate " + this + " twice");
        }
        CompletableFuture.runAsync(() ->  {
            while(isAlive) {
                action.run();
            }
        });
        return this;
    }

    public synchronized void kill() {
        isAlive = false;
    }
}
