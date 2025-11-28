package com.ck.pool.exception;

/**
 * Base exception for all resource pool errors.
 */
public class PoolException extends RuntimeException {
    public PoolException(String message) {
        super(message);
    }

    public PoolException(String message, Throwable cause) {
        super(message, cause);
    }
}
