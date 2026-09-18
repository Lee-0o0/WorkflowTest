package com.workflowtest.engine;

import com.workflowtest.engine.api.DefinitionModels.*;
import com.workflowtest.engine.api.DefinitionService;
import com.workflowtest.engine.api.BackupService;
import com.workflowtest.engine.api.ExecutionModels.GroupExecutionCommand;
import com.workflowtest.engine.api.ExecutionModels.PackageExecutionCommand;
import com.workflowtest.engine.api.ExecutionModels.Status;
import com.workflowtest.engine.api.WorkflowExecutionService;
import com.workflowtest.engine.api.ExecutionQueryService;
import com.workflowtest.engine.config.WorkflowTestEngineConfiguration;
import com.workflowtest.engine.executor.HttpStepExecutor;
import com.workflowtest.engine.executor.SqlStepExecutor;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.net.InetSocketAddress;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = EngineIntegrationTest.TestApplication.class)
class EngineIntegrationTest {
    private static final Path DATA_DIR;
    static {
        try { DATA_DIR = Files.createTempDirectory("workflow-engine-test"); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(WorkflowTestEngineConfiguration.class)
    static class TestApplication {}

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("workflowtest.data-dir", () -> DATA_DIR.toString());
        registry.add("workflowtest.global.baseUrl", () -> "http://global");
    }

    private final DefinitionService definitions;
    private final WorkflowExecutionService executions;
    private final HttpStepExecutor httpExecutor;
    private final SqlStepExecutor sqlExecutor;
    private final ObjectMapper objectMapper;
    private final BackupService backups;
    private final ExecutionQueryService history;

    @Autowired
    EngineIntegrationTest(DefinitionService definitions, WorkflowExecutionService executions,
                          HttpStepExecutor httpExecutor, SqlStepExecutor sqlExecutor,
                          ObjectMapper objectMapper, BackupService backups, ExecutionQueryService history) {
        this.definitions = definitions; this.executions = executions;
        this.httpExecutor = httpExecutor; this.sqlExecutor = sqlExecutor;
        this.objectMapper = objectMapper; this.backups = backups;
        this.history = history;
    }

    @Test
    void hierarchyVariablesHooksAndGroupExecutionWorkTogether() throws Exception {
        Project project = definitions.saveProject(null, "订单项目", "integration");
        Group group = definitions.saveGroup(null, project.id(), "主流程", "", 1);
        Workflow workflow = definitions.saveWorkflow(null, group.id(), "支付流程", "", 1);

        definitions.saveVariable(variable(ScopeType.PROJECT, project.id(), "baseUrl", "http://project"));
        definitions.saveVariable(variable(ScopeType.GROUP, group.id(), "baseUrl", "http://group"));
        definitions.saveVariable(variable(ScopeType.WORKFLOW, workflow.id(), "baseUrl", "http://workflow"));
        assertThat(definitions.previewEnvironment(project.id(), group.id(), workflow.id()).effective())
                .containsEntry("baseUrl", "http://workflow");

        Hook groupHook = definitions.getOrCreateHook(OwnerType.GROUP, group.id(), HookType.BEFORE_GROUP);
        definitions.saveHookStep(groupHook.id(), delayStep(groupHook.id(), "groupSetup", 1, true));
        Hook workflowHook = definitions.getOrCreateHook(OwnerType.WORKFLOW, workflow.id(), HookType.BEFORE_WORKFLOW);
        definitions.saveHookStep(workflowHook.id(), delayStep(workflowHook.id(), "workflowSetup", 1, true));
        definitions.saveWorkflowStep(delayStep(workflow.id(), "run", 1, false));

        var result = executions.submitGroup(new GroupExecutionCommand(group.id(), Map.of("seed", 1)), event -> {})
                .future().get(10, TimeUnit.SECONDS);
        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(history.steps(result.executionId())).extracting(ExecutionQueryService.StepExecutionDetail::phase)
                .contains("BEFORE_GROUP", "BEFORE_WORKFLOW", "WORKFLOW");
        assertThat(definitions.loadTree().projects()).hasSize(1);

        Group secondGroup = definitions.saveGroup(null, project.id(), "次要流程", "", 2);
        definitions.moveGroup(secondGroup.id(), -1);
        assertThat(definitions.loadTree().projects().getFirst().groups().getFirst().group().id()).isEqualTo(secondGroup.id());
        Workflow secondWorkflow = definitions.saveWorkflow(null, group.id(), "退款流程", "", 2);
        definitions.moveWorkflow(secondWorkflow.id(), -1);
        var reorderedGroup = definitions.loadTree().projects().getFirst().groups().stream()
                .filter(node -> node.group().id().equals(group.id())).findFirst().orElseThrow();
        assertThat(reorderedGroup.workflows().getFirst().workflow().id()).isEqualTo(secondWorkflow.id());
        Step secondStep = definitions.saveWorkflowStep(delayStep(workflow.id(), "finish", 2, false));
        definitions.moveStep(secondStep.id(), false, -1);
        var reorderedWorkflow = definitions.loadTree().projects().getFirst().groups().stream()
                .flatMap(node -> node.workflows().stream()).filter(node -> node.workflow().id().equals(workflow.id()))
                .findFirst().orElseThrow();
        assertThat(reorderedWorkflow.steps().getFirst().id()).isEqualTo(secondStep.id());

        definitions.saveVariable(new ScopedVariable(null, ScopeType.WORKFLOW, workflow.id(),
                "token", "AUTO", "secret-value", true, true));
        var secureEnvironment = definitions.previewEnvironment(project.id(), group.id(), workflow.id());
        assertThat(secureEnvironment.effective()).containsEntry("token", "secret-value");
        assertThat(secureEnvironment.redacted().effective()).containsEntry("token", "******");

        ExecutionContext context = new ExecutionContext(secureEnvironment, new java.util.LinkedHashMap<>(), Map.of(), objectMapper);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            RuntimeStep http = runtime("http", StepType.HTTP);
            var httpResult = httpExecutor.execute(http, objectMapper.readTree("""
                    {"method":"GET","url":"http://127.0.0.1:%d/health"}
                    """.formatted(server.getAddress().getPort())), context);
            assertThat(httpResult.response().path("status").asInt()).isEqualTo(200);
            assertThat(httpResult.response().path("body").path("ok").asBoolean()).isTrue();
        } finally { server.stop(0); }

        Path runtimeDb = DATA_DIR.resolve("runtime.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + runtimeDb);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample(id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("INSERT INTO sample(id,name) VALUES (1,'first')");
        }
        RuntimeDataSource source = definitions.saveDataSource(new RuntimeDataSource(null, project.id(),
                "runtime", "org.sqlite.JDBC", "jdbc:sqlite:" + runtimeDb, "", false, true), "");
        RuntimeStep sql = runtime("sql", StepType.SQL);
        var sqlResult = sqlExecutor.execute(sql, objectMapper.readTree("""
                {"datasourceId":"%s","operation":"QUERY","sql":"SELECT name FROM sample WHERE id=:id","parameters":{"id":1}}
                """.formatted(source.id())), context);
        assertThat(sqlResult.output().path("rows").get(0).path("name").asText()).isEqualTo("first");
        assertThat(backups.createBackup(DATA_DIR.resolve("backups"))).isRegularFile();

        var remoteDefinition = objectMapper.readTree("""
                {"environment":{"project":{"baseUrl":"http://remote"},"group":{},"workflow":{}},
                 "beforeWorkflow":{"steps":[{"code":"prepare","name":"prepare","type":"DELAY","config":{"millis":1}}]},
                 "steps":[{"code":"execute","name":"execute","type":"DELAY","config":{"millis":1}}]}
                """);
        String canonical = objectMapper.writeValueAsString(remoteDefinition);
        String checksum = "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var remotePackage = objectMapper.createObjectNode().put("workflowId", "remote-workflow").put("checksum", checksum);
        remotePackage.set("definition", remoteDefinition);
        var remoteResult = executions.submitPackage(new PackageExecutionCommand(remotePackage, Map.of()), event -> {})
                .future().get(10, TimeUnit.SECONDS);
        assertThat(remoteResult.status()).isEqualTo(Status.PASSED);
    }

    private ScopedVariable variable(ScopeType type, String scopeId, String key, Object value) {
        return new ScopedVariable(null, type, scopeId, key, "AUTO", value, false, true);
    }

    private Step delayStep(String ownerId, String code, int order, boolean hook) {
        return new Step(null, ownerId, code, code, StepType.DELAY, order, true,
                "{\"millis\":1}", "[]", "[]", FailureStrategy.STOP, "{}", hook);
    }

    private RuntimeStep runtime(String code, StepType type) {
        return new RuntimeStep(code, code, code, type, 0, "{}", "[]", "[]", FailureStrategy.STOP, "{}");
    }
}
