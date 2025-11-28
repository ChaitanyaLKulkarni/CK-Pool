package com.ck.pool;

import com.ck.pool.exception.ResourceAcquireTimeoutException;
import com.ck.pool.exception.ResourceCreationException;
import lombok.extern.slf4j.Slf4j;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
public class PooledResourceManager<T extends IPoolableResource> {
    private final BlockingQueue<PooledResourceWrapper<T>> available;
    private final Set<PooledResourceWrapper<T>> inUse = ConcurrentHashMap.newKeySet();
    private final Supplier<T> factory;
    private final PoolConfig config;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r->{
        Thread t = new Thread(r,"pooled-resource-cleaner");
        t.setDaemon(true);
        return t;
    });

    public PooledResourceManager(Supplier<T> factory, PoolConfig config) {
        log.debug("Initializing PooledResourceManager with config: {}", config);
        if(config.getMinSize() < 0 || config.getMaxSize() <= 0 || config.getMinSize() > config.getMaxSize()){
            throw new IllegalArgumentException("Invalid pool size configuration: minSize="+config.getMinSize()+", maxSize="+config.getMaxSize());
        }
        this.factory = factory;
        this.config = config;
        this.available = new LinkedBlockingQueue<>();

        if (config.isPreFill()) {
            log.debug("Pre-filling pool with {} resources", config.getMinSize());
            prefill();
        }

        startCleanupTask();
    }

    private void prefill() {
        for (int i = 0; i < config.getMinSize(); i++) {
            available.offer(createResource());
        }
    }

    private PooledResourceWrapper<T> createResource(){
        log.debug("Creating new pooled resource, current total size: {}, for thread: {}", totalSize(), Thread.currentThread().getName());
        try {
            T resource = factory.get();
            resource.init();
            log.debug("Resource created successfully: {}", resource);
            return new PooledResourceWrapper<>(resource);
        } catch (Exception e) {
            throw new ResourceCreationException("Failed to create resource", e);
        }
    }

    private T acquire(int numRetries) throws Exception {
        if(numRetries <= 0){
            throw new ResourceAcquireTimeoutException("Exceeded maximum acquire retries");
        }
        log.debug("Acquiring resource from pool, idle size: {}, in-use size: {}, total: {}, for thread: {}", available.size(), inUse.size(), totalSize(), Thread.currentThread().getName());
        PooledResourceWrapper<T> resourceWrapper = available.poll();

        if (resourceWrapper == null && totalSize() < config.getMaxSize()) {
            synchronized (this){
                if (totalSize() < config.getMaxSize()) {
                    log.debug("Pool not at max size, creating new resource");
                    resourceWrapper = createResource();
                }else{
                    log.debug("Pool reached max size during acquire, will wait for available resource");
                }
            }
        }

        if (resourceWrapper == null) {
            log.debug("No available resources, waiting up to {} ms", config.getWaitTimeoutMillis());
            resourceWrapper = available.poll(config.getWaitTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (resourceWrapper == null) {
                throw new ResourceAcquireTimeoutException("Timeout waiting for resource after waiting for "+config.getWaitTimeoutMillis()+" ms");
            }
            log.debug("Got resource from pool after waiting: {}", resourceWrapper.getResource());
        }

        T resource = resourceWrapper.getResource();

//        wait only for validation timeout when called isValid on resource
        if (!isResourceValidWithTimeout(resource)) {
            log.warn("Acquired resource is not valid, destroying and requesting a new one: {}", resource);
            safeDestroy(resourceWrapper);
            return acquire(numRetries - 1);
        }

        inUse.add(resourceWrapper);
        resourceWrapper.incrementUsage();
        resourceWrapper.touch();
        resource.reset();
        return resource;
    }

    public T acquire() throws Exception {
        return acquire(config.getAcquireRetryCount());
    }

    public void release(T resource) {
        log.debug("Releasing resource back to pool: {}", resource);
//        Find the wrapper in inUse set
        PooledResourceWrapper<T> resourceWrapper = null;
        for (PooledResourceWrapper<T> wrapper : inUse) {
            if (wrapper.getResource() == resource) {
                resourceWrapper = wrapper;
                break;
            }
        }
        if(resourceWrapper != null){
            inUse.remove(resourceWrapper);
            resourceWrapper.touch();
            if(config.isResetOnRelease()){
                if(resourceWrapper.getUsageCount() >= config.getResetAfterUses()){
                    log.debug("Resource {} reached max usage {}, destroying", resource, config.getResetAfterUses());
                    safeDestroy(resourceWrapper);
                    resourceWrapper = createResource();
                }
            }
            available.offer(resourceWrapper);
        }else{
            log.warn("Attempted to release unknown resource: {}", resource);
        }
    }

    public void destroyAll() {
        scheduler.shutdownNow();
        available.forEach(this::safeDestroy);
        inUse.forEach(this::safeDestroy);
        available.clear();
        inUse.clear();
    }

    private void safeDestroy(PooledResourceWrapper<T> resourceWrapper) {
        try { resourceWrapper.getResource().destroy(); } catch (Exception ignored) {}
    }

    private int totalSize() {
        return available.size() + inUse.size();
    }

    private boolean isResourceValidWithTimeout(T resource) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "resource-validation-check");
            t.setDaemon(true);
            return t;
        });

        Future<Boolean> future = executor.submit(resource::isValid);
        try {
            return future.get(config.getValidationTimeoutMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("Validation of resource {} timed out after {} ms", resource, config.getValidationTimeoutMillis());
            return false; // treat timeout as invalid
        } catch (Exception e) {
            log.warn("Error during validation of resource {}: {}", resource, e.getMessage());
            return false;
        } finally {
            executor.shutdownNow();
        }
    }

    private void startCleanupTask() {
        scheduler.scheduleAtFixedRate(() -> {
            if(available.size() <= config.getMinSize()){
                log.debug("Pool size at or below minimum, skipping idle resource cleanup");
                return; // no need to clean up
            }
            log.debug("Running idle resource cleanup task");
            Instant now = Instant.now();
            for (PooledResourceWrapper<T> resource : available) {
                Instant last = resource.getLastUsedAt();
                if (Duration.between(last, now).toMillis() > config.getIdleTimeoutMillis()) {
                    if(available.size() <= config.getMinSize()){
                        log.debug("Pool size at or below minimum, skipping destroying resource cleanup");
                        break;
                    }
                    log.info("Destroying idle resource: {}, idle for {}ms", resource.getResource(), Duration.between(last, now).toMillis());
                    available.remove(resource);
                    safeDestroy(resource);
                }
            }
        }, config.getIdleTimeoutMillis(), config.getIdleTimeoutMillis(), TimeUnit.MILLISECONDS);
    }

    // --- Optional: expose metrics ---
    public Map<String, Object> getMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("Idle", available.size());
        m.put("Active", inUse.size());
        m.put("Total / Max Size", totalSize() +" / "+ config.getMaxSize());
        m.put("Close and Open on Release", config.isResetOnRelease());
        m.put("Resource Usage Limit", config.getResetAfterUses());
        return m;
    }

    public int getTotalResources(){
        return totalSize();
    }
    public int getIdleResources(){
        return available.size();
    }
    public int getActiveResources(){
        return inUse.size();
    }
}
