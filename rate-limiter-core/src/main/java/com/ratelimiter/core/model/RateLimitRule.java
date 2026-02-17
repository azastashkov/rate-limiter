package com.ratelimiter.core.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitRule {

    @JsonProperty("id")
    private String id;

    @JsonProperty("path")
    private String path;

    @JsonProperty("algorithm")
    private AlgorithmType algorithm;

    @JsonProperty("maxRequests")
    private long maxRequests;

    @JsonProperty("windowSizeSeconds")
    private long windowSizeSeconds;

    @JsonProperty("bucketCapacity")
    private long bucketCapacity;

    @JsonProperty("refillRate")
    private double refillRate;

    @JsonProperty("leakRate")
    private double leakRate;

    @JsonProperty("keyResolver")
    private KeyResolverType keyResolver = KeyResolverType.IP;

    public RateLimitRule() {}

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public AlgorithmType getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(AlgorithmType algorithm) {
        this.algorithm = algorithm;
    }

    public long getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(long maxRequests) {
        this.maxRequests = maxRequests;
    }

    public long getWindowSizeSeconds() {
        return windowSizeSeconds;
    }

    public void setWindowSizeSeconds(long windowSizeSeconds) {
        this.windowSizeSeconds = windowSizeSeconds;
    }

    public long getBucketCapacity() {
        return bucketCapacity;
    }

    public void setBucketCapacity(long bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    public void setRefillRate(double refillRate) {
        this.refillRate = refillRate;
    }

    public double getLeakRate() {
        return leakRate;
    }

    public void setLeakRate(double leakRate) {
        this.leakRate = leakRate;
    }

    public KeyResolverType getKeyResolver() {
        return keyResolver;
    }

    public void setKeyResolver(KeyResolverType keyResolver) {
        this.keyResolver = keyResolver;
    }
}
