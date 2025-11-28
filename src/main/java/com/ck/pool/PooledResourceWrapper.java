package com.ck.pool;

import lombok.Getter;

import java.time.Instant;

@Getter
public class PooledResourceWrapper<T extends IPoolableResource>{
    private final T resource;
    private int usageCount;
    private final Instant createdAt;
    private Instant lastUsedAt;

    public PooledResourceWrapper(T resource) {
        this.resource = resource;
        this.usageCount = 0;
        this.createdAt = Instant.now();
    }

    public void incrementUsage() {
        usageCount++;
    }

    public void touch() {
        lastUsedAt = Instant.now();
    }
}
