package com.workflowtest.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServerApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void usersPermissionsDraftVersionAndExecutionPackageWork() throws Exception {
        String admin = login("admin", "ChangeMe123!");
        JsonNode user = request(post("/api/users"), admin, """
                {"username":"tester","displayName":"测试人员","password":"Password123!","systemRole":"USER","enabled":true}
                """, 200);
        String userId = user.path("id").asText();

        JsonNode project = request(post("/api/projects"), admin, """
                {"projectKey":"ORDER","name":"订单项目","description":"集中测试资产"}
                """, 200);
        String projectId = project.path("id").asText();
        request(put("/api/projects/" + projectId + "/members"), admin,
                "{\"userId\":\"" + userId + "\",\"role\":\"TEST_EXECUTOR\"}", 200);

        JsonNode group = request(post("/api/projects/" + projectId + "/groups"), admin,
                "{\"name\":\"支付组\",\"description\":\"\",\"sortOrder\":0}", 200);
        JsonNode workflow = request(post("/api/groups/" + group.path("id").asText() + "/workflows"), admin, """
                {"name":"支付流程","description":"","sortOrder":0,"draft":{"steps":[],"variables":{"baseUrl":"http://test"}}}
                """, 200);
        String workflowId = workflow.path("id").asText();

        JsonNode updated = request(put("/api/workflows/" + workflowId + "/draft"), admin, """
                {"expectedRevision":0,"draft":{"beforeWorkflow":{"steps":[]},"steps":[{"code":"health","type":"HTTP"}]}}
                """, 200);
        assertThat(updated.path("revision").asInt()).isEqualTo(1);
        request(post("/api/projects/" + projectId + "/variables"), admin,
                "{\"key\":\"baseUrl\",\"value\":\"http://project\",\"enabled\":true}", 200);
        request(post("/api/groups/" + group.path("id").asText() + "/variables"), admin,
                "{\"key\":\"timeout\",\"value\":5000,\"enabled\":true}", 200);
        mvc.perform(put("/api/workflows/" + workflowId + "/draft")
                        .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":0,\"draft\":{}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REVISION_CONFLICT"));

        JsonNode version = request(post("/api/workflows/" + workflowId + "/publish"), admin, null, 200);
        assertThat(version.path("version").asInt()).isEqualTo(1);
        assertThat(version.path("checksum").asText()).startsWith("sha256:");

        String tester = login("tester", "Password123!");
        mvc.perform(put("/api/workflows/" + workflowId + "/draft")
                        .header("Authorization", "Bearer " + tester).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedRevision\":1,\"draft\":{}}"))
                .andExpect(status().isForbidden());
        JsonNode executionPackage = request(get("/api/workflow-versions/" + version.path("id").asText() + "/execution-package"), tester, null, 200);
        assertThat(executionPackage.path("workflowVersionId").asText()).isEqualTo(version.path("id").asText());
        assertThat(executionPackage.path("definition").path("steps")).hasSize(1);
        assertThat(executionPackage.path("definition").path("environment").path("project").path("baseUrl").asText())
                .isEqualTo("http://project");

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + tester))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
    }

    private String login(String username, String password) throws Exception {
        String content = json.writeValueAsString(java.util.Map.of("username", username, "password", password));
        JsonNode response = request(post("/api/auth/login"), null, content, 200);
        return response.path("token").asText();
    }

    private JsonNode request(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                             String token, String body, int status) throws Exception {
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(body);
        String content = mvc.perform(builder).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return content.isBlank() ? json.createObjectNode() : json.readTree(content);
    }
}
