package com.workflowtest.engine.executor.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.support.EngineMessages;

import java.util.LinkedHashMap;
import java.util.Map;

public record HttpStepConfig(
        String method,
        String url,
        Map<String, String> headers,
        JsonNode body,
        Integer connectTimeoutMs,
        Integer readTimeoutMs
) implements StepConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 10000;

    public HttpStepConfig {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public static HttpStepConfig from(JsonNode node, ObjectMapper mapper) {
        Map<String, String> headers = new LinkedHashMap<>();
        JsonNode headersNode = node.path("headers");
        if (headersNode.isObject()) {
            headersNode.fields().forEachRemaining(entry -> headers.put(entry.getKey(), entry.getValue().asText()));
        }
        JsonNode bodyNode = node.get("body");
        JsonNode body = bodyNode == null || bodyNode.isNull() ? null : bodyNode;
        Integer connectTimeout = readOptionalInt(node, "connectTimeout");
        Integer readTimeout = readOptionalInt(node, "readTimeout");
        return new HttpStepConfig(
                node.path("method").asText("GET"),
                node.path("url").asText(),
                headers,
                body,
                connectTimeout,
                readTimeout);
    }

    public String resolvedMethod() {
        return method == null || method.isBlank() ? "GET" : method.toUpperCase();
    }

    public void validate() {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException(EngineMessages.HTTP_URL_REQUIRED);
        }
    }

    public int resolvedConnectTimeoutMs() {
        return connectTimeoutMs != null ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
    }

    public int resolvedReadTimeoutMs() {
        return readTimeoutMs != null ? readTimeoutMs : DEFAULT_READ_TIMEOUT_MS;
    }

    private static Integer readOptionalInt(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asInt();
    }
}
