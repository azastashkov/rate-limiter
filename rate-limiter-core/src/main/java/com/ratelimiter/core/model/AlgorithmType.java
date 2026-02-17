package com.ratelimiter.core.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AlgorithmType {
    TOKEN_BUCKET("token_bucket"),
    LEAKING_BUCKET("leaking_bucket"),
    FIXED_WINDOW("fixed_window"),
    SLIDING_WINDOW_LOG("sliding_window_log"),
    SLIDING_WINDOW_COUNTER("sliding_window_counter");

    private final String value;

    AlgorithmType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public static AlgorithmType fromValue(String value) {
        for (AlgorithmType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown algorithm type: " + value);
    }
}
