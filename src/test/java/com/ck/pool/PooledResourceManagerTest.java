package com.ck.pool;

import com.ck.pool.exception.ResourceAcquireTimeoutException;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PooledResourceManagerTest {

    private PooledResourceManager<MockResource> pool;
    private final PoolConfig config = PoolConfig.builder()
            .minSize(2)
            .maxSize(4)
            .waitTimeoutMillis(200)
            .idleTimeoutMillis(300)
            .preFill(true)
            .resetOnRelease(true)
            .resetAfterUses(3)
            .acquireRetryCount(3)
            .validationTimeoutMillis(100)
            .build();

    @BeforeEach
    void setup() {
        pool = new PooledResourceManager<>(MockResource::new, config);
    }

    @AfterEach
    void teardown() {
        pool.destroyAll();
    }

    @Test
    void shouldPreFillPool() {
        assertEquals(config.getMinSize(), pool.getIdleResources());
    }

    @Test
    void shouldAcquireAndReleaseResource() throws Exception {
        MockResource res = pool.acquire();
        assertNotNull(res);
        assertTrue(res.isValid());
        assertEquals(config.isPreFill()? config.getMinSize() : 1, pool.getTotalResources());
        assertEquals(1,pool.getActiveResources());

        pool.release(res);
        assertEquals(config.isPreFill()? config.getMinSize() : 1,pool.getIdleResources());
    }

    @Test
    void shouldCreateNewResourceWhenPoolIsEmpty() throws Exception {
        for(int i = 0; i < config.getMinSize(); i++) {
            pool.acquire();
        }
        MockResource r3 = pool.acquire(); // should create new

        assertNotNull(r3);
        assertEquals(config.getMinSize()+1, pool.getTotalResources());
    }

    @Test
    void shouldThrowTimeoutWhenNoResourceAvailable() {
        assertThrows(ResourceAcquireTimeoutException.class, () -> {
            // exhaust all resources
            for(int i = 0; i < config.getMaxSize(); i++) {
                pool.acquire();
            }
            // pool is full now, next acquire will timeout
            pool.acquire();
        });
    }

    @Test
    void shouldReplaceResourceWhenResetAfterUsesReached() throws Exception {
        int org = config.getMinSize();
        config.setMinSize(1);
        pool = new PooledResourceManager<>(MockResource::new,
                config);
        MockResource res = null;
        for (int i = 0; i < config.getResetAfterUses(); i++) {
            res = pool.acquire();
            assertEquals(i + 1,res.getUseCount());
            pool.release(res);
        }
        res = pool.acquire();
        // After 3 uses, the resource should be replaced
        assertEquals(1, res.getUseCount());
        config.setMinSize(org);
    }

    @Test
    void shouldAlwaysHaveMinResources() throws Exception {
        // Acquire and release resources multiple times
        TimeUnit.MILLISECONDS.sleep(config.getIdleTimeoutMillis()*2);
        assertEquals(config.getMinSize(), pool.getIdleResources());
    }

    @Test
    void shouldDestroyIdleResourcesAfterTimeout() throws Exception {
        // Let idle cleanup run
        ArrayList<MockResource> resources = new ArrayList<>();
        for(int i=0; i<config.getMinSize()+1; i++) {
            resources.add(pool.acquire());
        }
//        Check if pool size is minSize+1
        assertEquals(config.getMinSize()+1, pool.getTotalResources());
        assertEquals(config.getMinSize()+1, pool.getActiveResources());
        resources.forEach(pool::release);

        TimeUnit.MILLISECONDS.sleep(config.getIdleTimeoutMillis() * 2);
        assertEquals(config.getMinSize(), pool.getTotalResources());
        assertEquals(config.getMinSize(), pool.getIdleResources());
    }

    @Test
    void shouldHandleInvalidResourceByRecreatingIt() throws Exception {
        MockResource res = pool.acquire();
        res.destroy(); // manually invalidate
        pool.release(res);

        MockResource newRes = pool.acquire();
        assertTrue(newRes.isValid());
    }
}
