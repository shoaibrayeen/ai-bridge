package com.aibridge.exception;

/**
 * Raised when waiting for a queue slot exceeds the configured timeout for a given config.
 */
public class QueueTimeoutException extends RuntimeException {

    public QueueTimeoutException(String configId) {
        super("Queue timeout while waiting for capacity for configId=" + configId);
    }

    public QueueTimeoutException(String configId, Throwable cause) {
        super("Queue timeout while waiting for capacity for configId=" + configId, cause);
    }
}
