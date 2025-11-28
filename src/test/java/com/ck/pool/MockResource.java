package com.ck.pool;

import java.util.concurrent.atomic.AtomicBoolean;

public class MockResource implements IPoolableResource {
    private final int id;
    private static int counter = 0;
    private final AtomicBoolean valid = new AtomicBoolean(true);
    private int useCount = 0;
    public MockResource() {
        this.id = ++counter;
    }

    @Override
    public void init() {
        valid.set(true);
    }

    @Override
    public void destroy() {
        valid.set(false);
    }

    @Override
    public boolean isValid() {
        return valid.get();
    }

    @Override
    public void reset() {
        useCount++;
        // no-op for testing
    }

    public int getUseCount() {
        return useCount;
    }

    @Override
    public String toString() {
        return "MockResource-" + id;
    }
}
