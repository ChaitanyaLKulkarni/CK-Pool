package com.ck.pool.exception;

/**
 * Thrown when releasing a resource back to the pool fails.
 */
public class ResourceReleaseException extends PoolException {
    public ResourceReleaseException(String message) {
        super(message);
    }

    public ResourceReleaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
