package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepExecutor;
import com.workflowtest.engine.runtime.StepResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HttpStepExecutor implements StepExecutor {
    private final ObjectMapper objectMapper;
    @Override public StepType supports() { return StepType.HTTP; }

    @Override
    public StepResult execute(RuntimeStep step, JsonNode config, ExecutionContext context) throws Exception {
        long started = System.nanoTime();
        String method = config.path("method").asText("GET").toUpperCase();
        String url = config.path("url").asText();
        if (url.isBlank()) throw new IllegalArgumentException("HTTP URL 不能为空");
        int connectTimeout = config.path("connectTimeoutMs").asInt(5000);
        int readTimeout = config.path("readTimeoutMs").asInt(10000);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(readTimeout));
        JsonNode headers = config.path("headers");
        if (headers.isObject()) headers.fields().forEachRemaining(e -> builder.header(e.getKey(), e.getValue().asText()));
        String body = config.has("body") && !config.get("body").isNull()
                ? (config.get("body").isTextual() ? config.get("body").asText() : objectMapper.writeValueAsString(config.get("body"))) : "";
        builder.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put("method", method); requestNode.put("url", url);
        requestNode.set("headers", headers.isObject() ? headers : objectMapper.createObjectNode());
        if (!body.isEmpty()) requestNode.put("body", body);

        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("status", response.statusCode());
        responseNode.set("headers", objectMapper.valueToTree(response.headers().map()));
        try { responseNode.set("body", objectMapper.readTree(response.body())); }
        catch (Exception ignored) { responseNode.put("body", response.body()); }

        ObjectNode output = objectMapper.createObjectNode();
        output.set("request", requestNode); output.set("response", responseNode);
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        output.put("elapsedMs", elapsed);
        return new StepResult(output, requestNode, responseNode, elapsed);
    }
}
