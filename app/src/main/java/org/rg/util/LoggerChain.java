package org.rg.util;

import java.util.function.Consumer;

public class LoggerChain {
    private static LoggerChain instance;
    private Consumer<String> exceptionLogger;
    private Consumer<String> debugLogger;

    private LoggerChain() {
        debugLogger = System.out::println;
        exceptionLogger = System.err::println;
    }

    public static LoggerChain getInstance() {
        if (instance == null) {
            synchronized (LoggerChain.class) {
                if (instance == null) {
                    instance = new LoggerChain();
                }
            }
        }
        return instance;
    }

    public void setExceptionLogger(Consumer<String> exceptionLogger) {
        this.exceptionLogger = exceptionLogger;
    }

    public void appendExceptionLogger(Consumer<String> exceptionLogger) {
        if (this.exceptionLogger != null) {
            this.exceptionLogger = this.exceptionLogger.andThen(exceptionLogger);
        } else {
            this.exceptionLogger = exceptionLogger;
        }
    }

    public void setDebugLogger(Consumer<String> debugLogger) {
        this.debugLogger = debugLogger;
    }

    public void appendDebugLogger(Consumer<String> debugLogger) {
        if (this.debugLogger != null) {
            this.debugLogger = this.debugLogger.andThen(debugLogger);
        } else {
            this.debugLogger = debugLogger;
        }
    }

    public void logError(String message) {
        exceptionLogger.accept(message);
    }

    public void logDebug(String message) {
        debugLogger.accept(message);
    }
}
