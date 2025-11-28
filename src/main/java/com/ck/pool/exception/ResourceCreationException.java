package com.ck.pool.exception;

/**
 * Thrown when a resource fails to be created.
 */
public class ResourceCreationException extends PoolException {
    public ResourceCreationException(String message) {
        super(message);
    }

    public ResourceCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
