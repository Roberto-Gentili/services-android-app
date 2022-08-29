package org.rg.util;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiPredicate;

public class AsyncLooper {

    private final Executor executor;
    private final Runnable action;
    private Boolean isAlive;
    private Runnable actionToBeExecutedAtStarting;
    private Runnable actionToBeExecutedWhenKilled;
    private Long waitingTimeAtTheEndOfEveryIteration;
    private Long waitingTimeAtTheStartOfEveryIteration;
    private BiPredicate<AsyncLooper, Throwable> excpetionHandler;

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
                    waitFor(waitingTimeAtTheStartOfEveryIteration);
                    action.run();
                    waitFor(waitingTimeAtTheEndOfEveryIteration);
                } catch (Throwable exc) {
                    isAlive = excpetionHandler != null && excpetionHandler.test(this, exc);
                }
            }
            if (actionToBeExecutedWhenKilled != null) {
                actionToBeExecutedWhenKilled.run();
            }
        }, executor);
        return this;
    }

    private void waitFor(Long timeMillis) {
        if (timeMillis != null && timeMillis > 0) {
            synchronized (timeMillis) {
                try {
                    timeMillis.wait(timeMillis);
                } catch (InterruptedException exc) {
                    LoggerChain.getInstance().logError(exc.getMessage());
                }
            }
        }
    }

    public AsyncLooper atTheStartOfEveryIterationWaitFor(long millis) {
        this.waitingTimeAtTheStartOfEveryIteration = millis;
        return this;
    }

    public AsyncLooper atTheEndOfEveryIterationWaitFor(long millis) {
        this.waitingTimeAtTheEndOfEveryIteration = millis;
        return this;
    }

    public AsyncLooper whenStarted(Runnable action) {
        this.actionToBeExecutedAtStarting = action;
        return this;
    }

    public AsyncLooper whenAnExceptionIsThrown(BiPredicate<AsyncLooper, Throwable> excpetionHandler) {
        this.excpetionHandler = excpetionHandler;
        return this;
    }

    public AsyncLooper whenKilled(Runnable action) {
        this.actionToBeExecutedWhenKilled = action;
        return this;
    }

    public synchronized void kill() {
        isAlive = false;
    }

    public boolean isAlive() {
        return isAlive != null && isAlive;
    }
}
