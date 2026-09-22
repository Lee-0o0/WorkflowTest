package com.workflowtest.desktop.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;

@Component
public class ServerClient {
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile String baseUrl;
    private volatile String token;

    public ServerClient(ObjectMapper json, @Value("${workflowtest.server.url:http://localhost:8080}") String baseUrl) {
        this.json = json; this.baseUrl = normalize(baseUrl);
    }

    public void configure(String url) { this.baseUrl = normalize(url); this.token = null; }
    public String baseUrl() { return baseUrl; }
    public boolean connected() { return token != null; }

    public JsonNode login(String username, String password) {
        token = null;
        JsonNode result = request("POST", "/api/auth/login", json.createObjectNode().put("username", username).put("password", password), false);
        String issuedToken = result.path("token").asText();
        if (issuedToken.isBlank()) throw new IllegalStateException("集中资产服务未返回登录令牌");
        token = issuedToken;
        return result.path("user");
    }
    public List<JsonNode> projects() { return list("/api/projects"); }
    public List<JsonNode> groups(String projectId) { return list("/api/projects/" + projectId + "/groups"); }
    public List<JsonNode> workflows(String groupId) { return list("/api/groups/" + groupId + "/workflows"); }
    public JsonNode createProject(String key, String name, String description) { return request("POST", "/api/projects", json.createObjectNode().put("projectKey", key).put("name", name).put("description", description), true); }
    public JsonNode createGroup(String projectId, String name, String description) { return request("POST", "/api/projects/" + projectId + "/groups", json.createObjectNode().put("name", name).put("description", description).put("sortOrder", 0), true); }
    public JsonNode createWorkflow(String groupId, String name, String description) {
        var body = json.createObjectNode().put("name", name).put("description", description).put("sortOrder", 0);
        body.set("draft", json.createObjectNode().set("steps", json.createArrayNode()));
        return request("POST", "/api/groups/" + groupId + "/workflows", body, true);
    }
    public JsonNode updateDraft(String workflowId, int revision, JsonNode draft) { return request("PUT", "/api/workflows/" + workflowId + "/draft", json.createObjectNode().put("expectedRevision", revision).set("draft", draft), true); }
    public JsonNode publish(String workflowId) { return request("POST", "/api/workflows/" + workflowId + "/publish", null, true); }
    public List<JsonNode> versions(String workflowId) { return list("/api/workflows/" + workflowId + "/versions"); }
    public JsonNode executionPackage(String versionId) { return request("GET", "/api/workflow-versions/" + versionId + "/execution-package", null, true); }
    public List<JsonNode> variables(String pluralScope, String id) { return list("/api/" + pluralScope + "/" + id + "/variables"); }
    public JsonNode createVariable(String pluralScope, String id, String key, JsonNode value) { return request("POST", "/api/" + pluralScope + "/" + id + "/variables", json.createObjectNode().put("key", key).set("value", value), true); }
    public List<JsonNode> users() { return list("/api/users"); }
    public JsonNode createUser(String username, String displayName, String password, String systemRole) {
        return request("POST", "/api/users", json.createObjectNode().put("username", username).put("displayName", displayName)
                .put("password", password).put("systemRole", systemRole).put("enabled", true), true);
    }
    public List<JsonNode> members(String projectId) { return list("/api/projects/" + projectId + "/members"); }
    public JsonNode saveMember(String projectId, String userId, String role) { return request("PUT", "/api/projects/" + projectId + "/members", json.createObjectNode().put("userId", userId).put("role", role), true); }

    private List<JsonNode> list(String path) {
        JsonNode value = request("GET", path, null, true);
        return value.isArray() ? json.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<List<JsonNode>>() {}) : List.of();
    }

    private JsonNode request(String method, String path, JsonNode body, boolean authenticated) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path)).timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/json");
            if (authenticated) {
                if (token == null) throw new IllegalStateException("请先登录集中资产服务");
                builder.header("Authorization", "Bearer " + token);
            }
            String content = body == null ? "" : json.writeValueAsString(body);
            if (body != null) builder.header("Content-Type", "application/json");
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(content));
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode result = response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ServerException(response.statusCode(), result.path("code").asText("HTTP_ERROR"), result.path("message").asText(response.body()));
            return result;
        } catch (ServerException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("无法访问 WorkflowTestServer: " + e.getMessage(), e); }
    }
    private String normalize(String value) {
        String normalized = value == null || value.isBlank() ? "http://localhost:8080" : value.strip().replaceAll("/+$", "");
        URI uri;
        try { uri = URI.create(normalized); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("服务地址格式不正确", e); }
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
            throw new IllegalArgumentException("服务地址必须是完整的 HTTP/HTTPS 地址");
        return normalized;
    }
    public static class ServerException extends RuntimeException {
        private final int status;
        public ServerException(int status, String code, String message) { super(code + ": " + message); this.status = status; }
        public int status() { return status; }
    }
}
