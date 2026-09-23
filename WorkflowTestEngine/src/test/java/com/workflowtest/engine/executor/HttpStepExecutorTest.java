package com.workflowtest.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.workflowtest.engine.api.definition.DefinitionModels.EffectiveEnvironment;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.runtime.StepResult;
import com.workflowtest.engine.runtime.StepResult.OutputField;
import com.workflowtest.engine.support.EngineMessages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpStepExecutorTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpStepExecutor executor;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        executor = new HttpStepExecutor(objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void supports_returnsHttp() {
        assertThat(executor.supports()).isEqualTo(StepType.HTTP);
    }

    @Test
    void execute_rejectsBlankUrl() {
        RuntimeStep step = runtimeStep("http");
        JsonNode config = objectMapper.createObjectNode().put("url", "   ");

        assertThatThrownBy(() -> executor.execute(step, config, emptyContext()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(EngineMessages.HTTP_URL_REQUIRED);
    }

    @Test
    void execute_get_parsesJsonResponse() throws Exception {
        startServer(exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            respond(exchange, 200, body);
        });

        StepResult result = executor.execute(runtimeStep("get"), config("""
                {"method":"GET","url":"%s"}
                """.formatted(url())), emptyContext());

        assertThat(result.response().path(OutputField.STATUS).asInt()).isEqualTo(200);
        assertThat(result.response().path("body").path("ok").asBoolean()).isTrue();
        assertThat(result.request().path("method").asText()).isEqualTo("GET");
        assertThat(result.output().path(OutputField.ELAPSED_MS).asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void execute_defaultsToGetWhenMethodMissing() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        startServer(exchange -> {
            method.set(exchange.getRequestMethod());
            respond(exchange, 204, new byte[0]);
        });

        executor.execute(runtimeStep("default-get"), config("""
                {"url":"%s"}
                """.formatted(url())), emptyContext());

        assertThat(method).hasValue("GET");
    }

    @Test
    void execute_post_sendsJsonBody() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 201, "{\"created\":true}".getBytes(StandardCharsets.UTF_8));
        });

        StepResult result = executor.execute(runtimeStep("create"), config("""
                {
                  "method":"POST",
                  "url":"%s",
                  "headers":{"Content-Type":"application/json"},
                  "body":{"name":"demo"}
                }
                """.formatted(url())), emptyContext());

        assertThat(requestBody).hasValue("{\"name\":\"demo\"}");
        assertThat(result.response().path(OutputField.STATUS).asInt()).isEqualTo(201);
        assertThat(result.response().path("body").path("created").asBoolean()).isTrue();
        assertThat(result.request().path("body").asText()).isEqualTo("{\"name\":\"demo\"}");
    }

    @Test
    void execute_sendsCustomHeaders() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        startServer(exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{}".getBytes(StandardCharsets.UTF_8));
        });

        executor.execute(runtimeStep("auth"), config("""
                {
                  "method":"GET",
                  "url":"%s",
                  "headers":{"Authorization":"Bearer token-1"}
                }
                """.formatted(url())), emptyContext());

        assertThat(authHeader).hasValue("Bearer token-1");
    }

    @Test
    void execute_nonJsonBody_isKeptAsText() throws Exception {
        startServer(exchange -> respond(exchange, 200, "plain-text".getBytes(StandardCharsets.UTF_8)));

        StepResult result = executor.execute(runtimeStep("plain"), config("""
                {"method":"GET","url":"%s"}
                """.formatted(url())), emptyContext());

        assertThat(result.response().path("body").asText()).isEqualTo("plain-text");
    }

    @Test
    void execute_usesLayeredTimeoutFromWorkflowEnvironment() throws Exception {
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of(), Map.of(), Map.of(),
                Map.of("readTimeout", 15000, "connectTimeout", 8000),
                Map.of("readTimeout", 15000, "connectTimeout", 8000),
                Map.of());
        ExecutionContext context = new ExecutionContext(environment, Map.of(), Map.of(), objectMapper);

        startServer(exchange -> respond(exchange, 200, "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));

        StepResult result = executor.execute(runtimeStep("timeout"), config("""
                {"method":"GET","url":"%s"}
                """.formatted(url())), context);

        assertThat(result.response().path(OutputField.STATUS).asInt()).isEqualTo(200);
        assertThat(context.resolveLayeredInt("readTimeout", null)).isEqualTo(15000);
    }

    @Test
    void execute_stepConfigOverridesLayeredTimeout() throws Exception {
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of(), Map.of(), Map.of(),
                Map.of("readTimeout", 15000),
                Map.of("readTimeout", 15000),
                Map.of());
        ExecutionContext context = new ExecutionContext(environment, Map.of(), Map.of(), objectMapper);

        assertThat(context.resolveLayeredInt("readTimeout", objectMapper.readTree("{\"readTimeout\":3000}")))
                .isEqualTo(3000);
    }

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        server.start();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private ExecutionContext emptyContext() {
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        return new ExecutionContext(environment, Map.of(), Map.of(), objectMapper);
    }

    private JsonNode config(String json) throws Exception {
        return objectMapper.readTree(json);
    }

    private static RuntimeStep runtimeStep(String code) {
        return new RuntimeStep(1L, code, code, StepType.HTTP, 1, "{}", "[]", "[]");
    }

    @FunctionalInterface
    private interface HttpHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
