package com.workflowtest.engine.model;

import java.util.Map;

public record EffectiveEnvironment(
        Map<String, Object> global,
        Map<String, Object> project,
        Map<String, Object> group,
        Map<String, Object> workflow,
        Map<String, Object> effective,
        Map<String, String> sources) {}
