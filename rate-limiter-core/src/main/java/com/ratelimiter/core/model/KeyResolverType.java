package com.ratelimiter.core.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum KeyResolverType {
    IP("ip"),
    USER("user"),
    PATH("path"),
    IP_PATH("ip_path");

    private final String value;

    KeyResolverType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
