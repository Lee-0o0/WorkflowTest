package com.workflowtest.engine.executor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public record DelayStepConfig(long millis) implements StepConfig {

    private static final long DEFAULT_MILLIS = 1000L;
    private static final long MAX_MILLIS = 300_000L;

    public static DelayStepConfig from(JsonNode node, ObjectMapper mapper) {
        long millis = node.path("millis").asLong(DEFAULT_MILLIS);
        if (millis < 0 || millis > MAX_MILLIS) {
            throw new IllegalArgumentException("延迟必须在 0 到 300000ms 之间");
        }
        return new DelayStepConfig(millis);
    }
}
