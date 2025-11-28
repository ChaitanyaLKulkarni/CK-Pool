package com.ck.pool;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PoolConfig {
    private int maxSize;
    @Builder.Default
    private int minSize=1;
    @Builder.Default
    private long idleTimeoutMillis=30000;
    @Builder.Default
    private long waitTimeoutMillis=30000;
    @Builder.Default
    private long validationTimeoutMillis=5000;
    @Builder.Default
    private int acquireRetryCount = 3;

    private boolean resetOnRelease;
    private int resetAfterUses;
    private boolean preFill;
}
