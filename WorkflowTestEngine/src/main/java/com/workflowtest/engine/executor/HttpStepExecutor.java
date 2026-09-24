package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.executor.config.HttpStepConfig;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.StepResult;
import com.workflowtest.engine.runtime.StepResult.OutputField;
import com.workflowtest.engine.support.CommonConstant;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@RequiredArgsConstructor
public class HttpStepExecutor extends TypedStepExecutor<HttpStepConfig> {
    private static final class Field {
        static final String METHOD = "method";
        static final String URL = "url";
        static final String HEADERS = "headers";
        static final String BODY = "body";
        static final String CONNECT_TIMEOUT = "connectTimeout";
        static final String READ_TIMEOUT = "readTimeout";
        static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
        static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    }

    private final ObjectMapper objectMapper;

    @Override
    protected Class<HttpStepConfig> configType() {
        return HttpStepConfig.class;
    }

    @Override
    public StepType supports() {
        return StepType.HTTP;
    }

    @Override
    protected StepResult doExecute(RuntimeStep step, HttpStepConfig config, ExecutionContext context,
                                   RuntimeVariableScope variableScope) throws Exception {
        config.validate();
        long started = System.nanoTime();
        String method = config.resolvedMethod();
        String url = config.url();
        int connectTimeout = resolveTimeout(context, Field.CONNECT_TIMEOUT, config.connectTimeoutMs(), Field.DEFAULT_CONNECT_TIMEOUT_MS);
        int readTimeout = resolveTimeout(context, Field.READ_TIMEOUT, config.readTimeoutMs(), Field.DEFAULT_READ_TIMEOUT_MS);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeout)).build();
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(readTimeout));
        config.headers().forEach(builder::header);
        String body = serializeBody(config);
        builder.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        ObjectNode requestNode = objectMapper.createObjectNode();
        requestNode.put(Field.METHOD, method);
        requestNode.put(Field.URL, url);
        requestNode.set(Field.HEADERS, objectMapper.valueToTree(config.headers()));
        if (!body.isEmpty()) {
            requestNode.put(Field.BODY, body);
        }

        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put(OutputField.STATUS, response.statusCode());
        responseNode.set(Field.HEADERS, objectMapper.valueToTree(response.headers().map()));
        try {
            responseNode.set(Field.BODY, objectMapper.readTree(response.body()));
        } catch (Exception ignored) {
            responseNode.put(Field.BODY, response.body());
        }

        ObjectNode output = objectMapper.createObjectNode();
        output.set(OutputField.REQUEST, requestNode);
        output.set(OutputField.RESPONSE, responseNode);
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        output.put(OutputField.ELAPSED_MS, elapsed);
        return new StepResult(output, requestNode, responseNode, elapsed);
    }

    private String serializeBody(HttpStepConfig config) throws Exception {
        if (config.body() == null || config.body().isNull()) {
            return CommonConstant.EMPTY;
        }
        return config.body().isTextual() ? config.body().asText() : objectMapper.writeValueAsString(config.body());
    }

    private int resolveTimeout(ExecutionContext context, String key, Integer localValue, int fallback) {
        if (localValue != null) {
            return localValue;
        }
        Integer layered = context.resolveLayeredInt(key, null);
        return layered != null ? layered : fallback;
    }
}
