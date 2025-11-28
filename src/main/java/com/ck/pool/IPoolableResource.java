package com.ck.pool;

public interface IPoolableResource extends AutoCloseable {
    /** Called once when the resource is first created */
    void init() throws Exception;

    /** Called before reusing a resource (optional) */
    default void reset() throws Exception {}

    /** Called before permanently destroying a resource */
    void destroy() throws Exception;

    /** Optional: Validate resource health */
    default boolean isValid() { return true; }

    default void close() throws Exception {
        destroy();
    }
}
