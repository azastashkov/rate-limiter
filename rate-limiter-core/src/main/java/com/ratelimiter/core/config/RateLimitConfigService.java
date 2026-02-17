package com.ratelimiter.core.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.core.model.RateLimitRule;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.NodeCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class RateLimitConfigService implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfigService.class);
    private static final String CONFIG_PATH = "/rate-limiter/rules";

    private final CuratorFramework curator;
    private final ObjectMapper objectMapper;
    private final List<Consumer<List<RateLimitRule>>> listeners = new CopyOnWriteArrayList<>();
    private volatile List<RateLimitRule> currentRules = Collections.emptyList();
    private NodeCache nodeCache;

    public RateLimitConfigService(CuratorFramework curator, ObjectMapper objectMapper) {
        this.curator = curator;
        this.objectMapper = objectMapper;
    }

    public void start() throws Exception {
        ensurePathExists();
        loadRules();
        watchForChanges();
    }

    private void ensurePathExists() throws Exception {
        if (curator.checkExists().forPath(CONFIG_PATH) == null) {
            curator.create().creatingParentsIfNeeded().forPath(CONFIG_PATH, "[]".getBytes());
            log.info("Created ZooKeeper path: {}", CONFIG_PATH);
        }
    }

    private void loadRules() {
        try {
            byte[] data = curator.getData().forPath(CONFIG_PATH);
            List<RateLimitRule> rules = objectMapper.readValue(data, new TypeReference<>() {});
            this.currentRules = List.copyOf(rules);
            log.info("Loaded {} rate limit rules from ZooKeeper", rules.size());
        } catch (Exception e) {
            log.error("Failed to load rate limit rules from ZooKeeper", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void watchForChanges() throws Exception {
        nodeCache = new NodeCache(curator, CONFIG_PATH);
        nodeCache.getListenable().addListener(() -> {
            log.info("Rate limit rules changed in ZooKeeper, reloading...");
            loadRules();
            notifyListeners();
        });
        nodeCache.start(true);
    }

    private void notifyListeners() {
        List<RateLimitRule> rules = currentRules;
        for (Consumer<List<RateLimitRule>> listener : listeners) {
            try {
                listener.accept(rules);
            } catch (Exception e) {
                log.error("Error notifying listener of rule changes", e);
            }
        }
    }

    public List<RateLimitRule> getRules() {
        return currentRules;
    }

    public void addListener(Consumer<List<RateLimitRule>> listener) {
        listeners.add(listener);
    }

    public void updateRules(List<RateLimitRule> rules) throws Exception {
        byte[] data = objectMapper.writeValueAsBytes(rules);
        curator.setData().forPath(CONFIG_PATH, data);
        log.info("Updated {} rate limit rules in ZooKeeper", rules.size());
    }

    @Override
    public void close() throws Exception {
        if (nodeCache != null) {
            nodeCache.close();
        }
    }
}
