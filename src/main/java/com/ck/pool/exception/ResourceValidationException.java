package com.ck.pool.exception;

/**
 * Thrown when a resource fails validation before being borrowed.
 */
public class ResourceValidationException extends PoolException {
    public ResourceValidationException(String message) {
        super(message);
    }
}
