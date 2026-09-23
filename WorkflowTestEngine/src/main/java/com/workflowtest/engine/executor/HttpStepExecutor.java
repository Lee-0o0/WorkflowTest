package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepResult;
import com.workflowtest.engine.runtime.StepResult.OutputField;
import com.workflowtest.engine.support.CommonConstant;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class HttpStepExecutor implements StepExecutor {
    private static final class ConfigField {
        static final String CONNECT_TIMEOUT = "connectTimeout";
        static final String READ_TIMEOUT = "readTimeout";
        static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
        static final int DEFAULT_READ_TIMEOUT_MS = 10000;
        static final String METHOD = "method";
        static final String URL = "url";
        static final String HEADERS = "headers";
        static final String BODY = "body";
        static final String DEFAULT_METHOD = "GET";
    }

    private final ObjectMapper objectMapper;

    @Override public StepType supports() {
        return StepType.HTTP;
    }

    @Override
    public StepResult execute(RuntimeStep step, JsonNode config, ExecutionContext context) throws Exception {
        long started = System.nanoTime();
        String method = config.path(ConfigField.METHOD).asText(ConfigField.DEFAULT_METHOD).toUpperCase();
        String url = config.path(ConfigField.URL).asText();
        if (url.isBlank()) {
            throw new IllegalArgumentException(EngineMessages.HTTP_URL_REQUIRED);
        }

        int connectTimeout = resolveTimeout(config, context, ConfigField.CONNECT_TIMEOUT, ConfigField.DEFAULT_CONNECT_TIMEOUT_MS);
        int readTimeout = resolveTimeout(config, context, ConfigField.READ_TIMEOUT, ConfigField.DEFAULT_READ_TIMEOUT_MS);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(readTimeout));
        JsonNode headers = config.path(ConfigField.HEADERS);
        if (headers.isObject()) headers.fields().forEachRemaining(e -> builder.header(e.getKey(), e.getValue().asText()));
        String body = config.has(ConfigField.BODY) && !config.get(ConfigField.BODY).isNull()
                ? (config.get(ConfigField.BODY).isTextual() ? config.get(ConfigField.BODY).asText() : objectMapper.writeValueAsString(config.get(ConfigField.BODY))) : CommonConstant.EMPTY;
        builder.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put(ConfigField.METHOD, method); requestNode.put(ConfigField.URL, url);
        requestNode.set(ConfigField.HEADERS, headers.isObject() ? headers : objectMapper.createObjectNode());
        if (!body.isEmpty()) requestNode.put(ConfigField.BODY, body);

        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put(OutputField.STATUS, response.statusCode());
        responseNode.set(ConfigField.HEADERS, objectMapper.valueToTree(response.headers().map()));
        try { responseNode.set(ConfigField.BODY, objectMapper.readTree(response.body())); }
        catch (Exception ignored) { responseNode.put(ConfigField.BODY, response.body()); }

        ObjectNode output = objectMapper.createObjectNode();
        output.set(OutputField.REQUEST, requestNode);
        output.set(OutputField.RESPONSE, responseNode);
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        output.put(OutputField.ELAPSED_MS, elapsed);
        return new StepResult(output, requestNode, responseNode, elapsed);
    }

    private int resolveTimeout(JsonNode config, ExecutionContext context, String key, int fallback) {
        Integer layered = context.resolveLayeredInt(key, config);
        return layered != null ? layered : fallback;
    }
}
