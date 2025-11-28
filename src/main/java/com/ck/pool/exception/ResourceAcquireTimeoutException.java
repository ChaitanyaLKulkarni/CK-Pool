package com.ck.pool.exception;

/**
 * Thrown when acquiring a resource from the pool times out.
 */
public class ResourceAcquireTimeoutException extends PoolException {
    public ResourceAcquireTimeoutException(String message) {
        super(message);
    }
}
