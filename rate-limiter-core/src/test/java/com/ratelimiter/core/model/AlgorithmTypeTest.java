package com.ratelimiter.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmTypeTest {

    @Test
    void fromValidValues() {
        assertEquals(AlgorithmType.TOKEN_BUCKET, AlgorithmType.fromValue("token_bucket"));
        assertEquals(AlgorithmType.LEAKING_BUCKET, AlgorithmType.fromValue("leaking_bucket"));
        assertEquals(AlgorithmType.FIXED_WINDOW, AlgorithmType.fromValue("fixed_window"));
        assertEquals(AlgorithmType.SLIDING_WINDOW_LOG, AlgorithmType.fromValue("sliding_window_log"));
        assertEquals(AlgorithmType.SLIDING_WINDOW_COUNTER, AlgorithmType.fromValue("sliding_window_counter"));
    }

    @Test
    void fromInvalidValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> AlgorithmType.fromValue("invalid"));
    }

    @Test
    void getValueReturnsCorrectString() {
        assertEquals("token_bucket", AlgorithmType.TOKEN_BUCKET.getValue());
        assertEquals("leaking_bucket", AlgorithmType.LEAKING_BUCKET.getValue());
    }
}
