package org.rg.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class AsyncLooper {

    private Executor executor;
    private Boolean isAlive;
    private Runnable action;
    private Runnable actionToBeExecutedAtStarting;
    private Runnable actionToBeExecutedWhenKilled;
    private Long waitingTimeAtTheEndOfEveryIteration;
    private Long waitingTimeAtTheStartOfEveryIteration;

    public AsyncLooper(Runnable action, Executor executor) {
        this.action = action;
        this.executor = executor;
    }

    public synchronized AsyncLooper activate() {
        if (isAlive == null) {
            isAlive = true;
        } else {
            throw new IllegalStateException("Could not activate " + this + " twice");
        }
        CompletableFuture.runAsync(() ->  {
            if (actionToBeExecutedAtStarting != null) {
                actionToBeExecutedAtStarting.run();
            }
            while(isAlive) {
                try {
                    if (waitingTimeAtTheStartOfEveryIteration != null && waitingTimeAtTheStartOfEveryIteration > 0) {
                        synchronized (waitingTimeAtTheStartOfEveryIteration) {
                            try {
                                waitingTimeAtTheStartOfEveryIteration.wait(waitingTimeAtTheStartOfEveryIteration);
                            } catch (InterruptedException exc) {
                                LoggerChain.getInstance().logError(exc.getMessage());
                            }
                        }
                    }
                    action.run();
                    if (waitingTimeAtTheEndOfEveryIteration != null && waitingTimeAtTheEndOfEveryIteration > 0) {
                        synchronized (waitingTimeAtTheEndOfEveryIteration) {
                            try {
                                waitingTimeAtTheEndOfEveryIteration.wait(waitingTimeAtTheEndOfEveryIteration);
                            } catch (InterruptedException exc) {
                                LoggerChain.getInstance().logError(exc.getMessage());
                            }
                        }
                    }
                } catch (Throwable exc) {
                    isAlive = false;
                }
            }
            if (actionToBeExecutedWhenKilled != null) {
                actionToBeExecutedWhenKilled.run();
            }
        }, executor);
        return this;
    }

    public AsyncLooper atTheStartOfEveryIterationWaitFor(Long millis) {
        this.waitingTimeAtTheStartOfEveryIteration = millis;
        return this;
    }

    public AsyncLooper atTheEndOfEveryIterationWaitFor(Long millis) {
        this.waitingTimeAtTheEndOfEveryIteration = millis;
        return this;
    }

    public AsyncLooper whenStarted(Runnable action) {
        this.actionToBeExecutedAtStarting = action;
        return this;
    }

    public AsyncLooper whenKilled(Runnable action) {
        this.actionToBeExecutedWhenKilled = action;
        return this;
    }

    public synchronized void kill() {
        isAlive = false;
    }
}
