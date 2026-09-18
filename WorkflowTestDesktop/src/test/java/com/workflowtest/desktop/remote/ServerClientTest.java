package com.workflowtest.desktop.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServerClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void doesNotConnectUntilLoginAndAddsBearerTokenAfterLogin() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/login", exchange -> respond(exchange, 200,
                "{\"token\":\"token-123\",\"user\":{\"displayName\":\"测试员\"}}"));
        server.createContext("/api/projects", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "[{\"id\":\"p1\",\"name\":\"项目一\"}]");
        });
        server.start();

        ServerClient client = new ServerClient(new ObjectMapper(), baseUrl());
        assertThat(client.connected()).isFalse();
        assertThat(client.login("tester", "secret").path("displayName").asText()).isEqualTo("测试员");
        assertThat(client.connected()).isTrue();
        assertThat(client.projects()).hasSize(1);
        assertThat(authorization.get()).isEqualTo("Bearer token-123");
    }

    @Test
    void failedReloginClearsPreviousConnection() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/auth/login", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (request.contains("good")) respond(exchange, 200, "{\"token\":\"valid\",\"user\":{}}");
            else respond(exchange, 401, "{\"code\":\"UNAUTHORIZED\",\"message\":\"用户名或密码错误\"}");
        });
        server.start();

        ServerClient client = new ServerClient(new ObjectMapper(), baseUrl());
        client.login("good", "secret");
        assertThat(client.connected()).isTrue();
        assertThatThrownBy(() -> client.login("bad", "secret"))
                .isInstanceOf(ServerClient.ServerException.class)
                .hasMessageContaining("用户名或密码错误");
        assertThat(client.connected()).isFalse();
    }

    @Test
    void configureDisconnectsAndValidatesServerAddress() {
        ServerClient client = new ServerClient(new ObjectMapper(), "http://localhost:8080/");
        assertThat(client.baseUrl()).isEqualTo("http://localhost:8080");
        assertThatThrownBy(() -> client.configure("localhost:8080"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP/HTTPS");
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
