package com.workflowtest.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.workflowtest.engine.api.definition.*;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.execution.*;
import com.workflowtest.engine.api.execution.ExecutionModels.*;
import com.workflowtest.engine.api.support.BackupService;
import com.workflowtest.engine.api.support.EnvironmentPreviewService;
import com.workflowtest.engine.executor.HttpStepExecutor;
import com.workflowtest.engine.executor.SqlStepExecutor;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.engine.support.FactoryRegisteredExecutionListener;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class EngineIntegrationTest extends BasicTestApplication {
    private static final Path DATA_DIR = dataDir(EngineIntegrationTest.class);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerDataDir(registry, DATA_DIR);
        registry.add("workflowtest.global.baseUrl", () -> "http://global");
    }

    private final ProjectService projectService;
    private final ProjectTreeService projectTree;
    private final WorkflowGroupService workflowGroups;
    private final WorkflowDefinitionService workflowDefinitions;
    private final StepDefinitionService stepDefinitions;
    private final HookDefinitionService hookDefinitions;
    private final ScopedVariableService scopedVariables;
    private final ProjectResourceService projectResources;
    private final EnvironmentPreviewService environmentPreview;
    private final ProjectExecutionService projectExecutions;
    private final GroupExecutionService groupExecutions;
    private final WorkflowRunService workflowRuns;
    private final PackageExecutionService packageExecutions;
    private final StepExecutionQueryService stepExecutions;
    private final HttpStepExecutor httpExecutor;
    private final SqlStepExecutor sqlExecutor;
    private final ObjectMapper objectMapper;
    private final BackupService backups;
    private final DataSource dataSource;

    @Test
    void hierarchyVariablesHooksAndGroupExecutionWorkTogether() throws Exception {
        FactoryRegisteredExecutionListener.reset();
        Project project = projectService.save(null, "订单项目", "integration");
        Group group = workflowGroups.save(null, project.id(), "主流程", "", 1);
        Workflow workflow = workflowDefinitions.save(null, group.id(), "支付流程", "", 1);

        scopedVariables.save(variable(ScopeType.PROJECT, project.id(), "baseUrl", "http://project"));
        scopedVariables.save(variable(ScopeType.GROUP, group.id(), "baseUrl", "http://group"));
        scopedVariables.save(variable(ScopeType.WORKFLOW, workflow.id(), "baseUrl", "http://workflow"));
        assertThat(environmentPreview.preview(project.id(), group.id(), workflow.id()).effective())
                .containsEntry("baseUrl", "http://workflow");

        Hook beforeGroupHook = hookDefinitions.find(group.id(), HookType.BEFORE_GROUP).orElseThrow();
        stepDefinitions.saveHookStep(beforeGroupHook.id(), delayStep(beforeGroupHook.id(), "groupSetup", 1, true));
        Hook afterGroupHook = hookDefinitions.find(group.id(), HookType.AFTER_GROUP).orElseThrow();
        stepDefinitions.saveHookStep(afterGroupHook.id(), delayStep(afterGroupHook.id(), "groupTeardown", 1, true));
        stepDefinitions.saveWorkflowStep(delayStep(workflow.id(), "run", 1, false));

        var result = groupExecutions.submit(new GroupExecutionCommand(group.id(), Map.of("seed", 1)))
                .future().get(10, TimeUnit.SECONDS);
        assertThat(result.status()).isEqualTo(Status.PASSED);
        assertThat(FactoryRegisteredExecutionListener.EVENT_COUNT.get()).isGreaterThan(0);

        var projectResult = projectExecutions.submit(new ProjectExecutionCommand(project.id(), Map.of()))
                .future().get(30, TimeUnit.SECONDS);
        assertThat(projectResult.status()).isEqualTo(Status.PASSED);
        assertThat(stepExecutions.listByExecution(result.executionId())).extracting(StepExecutionDetail::phase)
                .contains("BEFORE_GROUP", "WORKFLOW", "AFTER_GROUP");
        assertThat(projectTree.listProjects()).extracting(Project::id).contains(project.id());

        Group secondGroup = workflowGroups.save(null, project.id(), "次要流程", "", 2);
        workflowGroups.move(secondGroup.id(), -1);
        assertThat(projectTree.listGroups(project.id()).getFirst().id()).isEqualTo(secondGroup.id());
        Workflow secondWorkflow = workflowDefinitions.save(null, group.id(), "退款流程", "", 2);
        workflowDefinitions.move(secondWorkflow.id(), -1);
        assertThat(projectTree.listWorkflows(group.id()).getFirst().id()).isEqualTo(secondWorkflow.id());
        Step secondStep = stepDefinitions.saveWorkflowStep(delayStep(workflow.id(), "finish", 2, false));
        stepDefinitions.move(secondStep.id(), false, -1);
        assertThat(projectTree.listWorkflowSteps(workflow.id()).getFirst().id()).isEqualTo(secondStep.id());

        scopedVariables.save(new ScopedVariable(null, ScopeType.WORKFLOW, workflow.id(),
                "token", "AUTO", "secret-value", true));
        var tokenEnvironment = environmentPreview.preview(project.id(), group.id(), workflow.id());
        assertThat(tokenEnvironment.effective()).containsEntry("token", "secret-value");

        ExecutionContext context = new ExecutionContext(tokenEnvironment, new java.util.LinkedHashMap<>(), Map.of(), objectMapper);
        assertThat(context.resolve("global.baseUrl")).isEqualTo("http://global");
        assertThat(context.resolve("project.baseUrl")).isEqualTo("http://project");
        assertThat(context.resolve("group.baseUrl")).isEqualTo("http://group");
        assertThat(context.resolve("baseUrl")).isEqualTo("http://workflow");
        assertThat(context.resolve("workflow.baseUrl")).isEqualTo("http://workflow");
        context.putVariable("workflow.orderId", 12345, false);
        assertThat(context.resolve("workflow.orderId")).isEqualTo(12345);

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
            statement.execute("CREATE TABLE IF NOT EXISTS sample(id INTEGER PRIMARY KEY, name TEXT)");
            statement.execute("DELETE FROM sample");
            statement.execute("INSERT INTO sample(id,name) VALUES (1,'first')");
        }
        Map<String, Object> datasourceConfig = Map.of(
                "driverClass", "org.sqlite.JDBC",
                "jdbcUrl", "jdbc:sqlite:" + runtimeDb,
                "username", "",
                "allowDangerousSql", false);
        ProjectResource source = projectResources.save(
                new ProjectResource(null, project.id(), ProjectResourceType.DATASOURCE, "runtime", datasourceConfig, true), "");
        RuntimeStep sql = runtime("sql", StepType.SQL);
        var sqlResult = sqlExecutor.execute(sql, objectMapper.readTree("""
                {"datasourceId":"%s","operation":"QUERY","sql":"SELECT name FROM sample WHERE id=:id","parameters":{"id":1}}
                """.formatted(source.id())), context);
        assertThat(sqlResult.output().path("rows").get(0).path("name").asText()).isEqualTo("first");
        assertThat(backups.createBackup(DATA_DIR.resolve("backups"))).isRegularFile();

        var remoteDefinition = objectMapper.readTree("""
                {"environment":{"project":{"baseUrl":"http://remote"},"group":{},"workflow":{}},
                 "steps":[{"code":"execute","name":"execute","type":"DELAY","config":{"millis":1}}]}
                """);
        String canonical = objectMapper.writeValueAsString(remoteDefinition);
        String checksum = "sha256:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var remotePackage = objectMapper.createObjectNode().put("workflowId", "remote-workflow").put("checksum", checksum);
        remotePackage.set("definition", remoteDefinition);
        var remoteResult = packageExecutions.submit(new PackageExecutionCommand(remotePackage, Map.of()))
                .future().get(10, TimeUnit.SECONDS);
        assertThat(remoteResult.status()).isEqualTo(Status.PASSED);
    }

    @Test
    void failedWorkflowDoesNotBlockSiblingWorkflows() throws Exception {
        Project project = projectService.save(null, "隔离项目", "");
        Group group = workflowGroups.save(null, project.id(), "隔离组", "", 1);
        Workflow failing = workflowDefinitions.save(null, group.id(), "失败流", "", 1);
        Workflow succeeding = workflowDefinitions.save(null, group.id(), "成功流", "", 2);
        stepDefinitions.saveWorkflowStep(httpStep(failing.id(), "failHttp", 1, "http://127.0.0.1:1/unreachable"));
        stepDefinitions.saveWorkflowStep(delayStep(succeeding.id(), "okDelay", 1, false));

        var result = groupExecutions.submit(new GroupExecutionCommand(group.id(), Map.of()))
                .future().get(15, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo(Status.FAILED);
        Map<Long, String> statuses = workflowExecutionStatuses(group.id());
        assertThat(statuses).hasSize(2);
        assertThat(statuses.get(failing.id())).isEqualTo(Status.FAILED.name());
        assertThat(statuses.get(succeeding.id())).isEqualTo(Status.PASSED.name());
    }

    @Test
    void stepFailureStopsRemainingStepsInWorkflow() throws Exception {
        Project project = projectService.save(null, "步骤项目", "");
        Group group = workflowGroups.save(null, project.id(), "步骤组", "", 1);
        Workflow workflow = workflowDefinitions.save(null, group.id(), "步骤流", "", 1);
        stepDefinitions.saveWorkflowStep(delayStep(workflow.id(), "step1", 1, false));
        stepDefinitions.saveWorkflowStep(httpStep(workflow.id(), "step2", 2, "http://127.0.0.1:1/unreachable"));
        stepDefinitions.saveWorkflowStep(delayStep(workflow.id(), "step3", 3, false));

        var result = workflowRuns.submit(new WorkflowExecutionCommand(workflow.id(), Map.of()))
                .future().get(15, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(stepExecutions.listByExecution(result.executionId()))
                .filteredOn(d -> "WORKFLOW".equals(d.phase()))
                .extracting(StepExecutionDetail::stepCode)
                .containsExactly("step1", "step2");
    }

    private Map<Long, String> workflowExecutionStatuses(long groupId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT e.workflow_id, e.status FROM ts_execution e
                     JOIN ts_group_execution g ON e.group_execution_id = g.id
                     WHERE g.group_id = ?
                     ORDER BY e.id
                     """)) {
            statement.setLong(1, groupId);
            try (var rows = statement.executeQuery()) {
                Map<Long, String> statuses = new java.util.LinkedHashMap<>();
                while (rows.next()) statuses.put(rows.getLong(1), rows.getString(2));
                return statuses;
            }
        }
    }

    private ScopedVariable variable(ScopeType type, Long scopeId, String key, Object value) {
        return new ScopedVariable(null, type, scopeId, key, "AUTO", value, true);
    }

    private Step httpStep(Long ownerId, String code, int order, String url) {
        return new Step(null, ownerId, code, code, StepType.HTTP, order, true,
                "{\"method\":\"GET\",\"url\":\"" + url + "\"}", "[]", "[]", false);
    }

    private Step delayStep(Long ownerId, String code, int order, boolean hook) {
        return new Step(null, ownerId, code, code, StepType.DELAY, order, true,
                "{\"millis\":1}", "[]", "[]", hook);
    }

    private RuntimeStep runtime(String code, StepType type) {
        return new RuntimeStep(null, code, code, type, 0, "{}", "[]", "[]");
    }
}
