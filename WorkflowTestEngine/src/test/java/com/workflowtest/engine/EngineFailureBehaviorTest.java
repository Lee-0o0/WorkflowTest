package com.workflowtest.engine;

import com.workflowtest.engine.api.definition.*;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.execution.ExecutionModels.*;
import com.workflowtest.engine.api.execution.GroupExecutionService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@RequiredArgsConstructor(onConstructor_ = @Autowired)
class EngineFailureBehaviorTest extends BasicTestApplication {
    private static final Path DATA_DIR = dataDir(EngineFailureBehaviorTest.class);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registerDataDir(registry, DATA_DIR);
    }

    private final ProjectService projectService;
    private final WorkflowGroupService workflowGroups;
    private final WorkflowDefinitionService workflowDefinitions;
    private final StepDefinitionService stepDefinitions;
    private final HookDefinitionService hookDefinitions;
    private final GroupExecutionService groupExecutions;
    private final DataSource dataSource;

    @Test
    void beforeGroupHookFailureSkipsWorkflows() throws Exception {
        Project project = projectService.save(null, "钩子项目", "");
        Group group = workflowGroups.save(null, project.id(), "钩子组", "", 1);
        Workflow workflow = workflowDefinitions.save(null, group.id(), "不应执行", "", 1);
        stepDefinitions.saveWorkflowStep(delayStep(workflow.id(), "skipped", 1));

        Hook beforeGroupHook = hookDefinitions.find(group.id(), HookType.BEFORE_GROUP).orElseThrow();
        stepDefinitions.saveHookStep(beforeGroupHook.id(),
                httpStep(beforeGroupHook.id(), "failHook", 1, "http://127.0.0.1:1/unreachable"));

        var result = groupExecutions.submit(new GroupExecutionCommand(group.id(), Map.of()), event -> {})
                .future().get(15, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo(Status.FAILED);
        assertThat(workflowExecutionCount(group.id())).isZero();
    }

    private long workflowExecutionCount(long groupId) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                     SELECT COUNT(*) FROM ts_execution e
                     JOIN ts_group_execution g ON e.group_execution_id = g.id
                     WHERE g.group_id = ?
                     """)) {
            statement.setLong(1, groupId);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private Step httpStep(Long ownerId, String code, int order, String url) {
        return new Step(null, ownerId, code, code, StepType.HTTP, order, true,
                "{\"method\":\"GET\",\"url\":\"" + url + "\"}", "[]", "[]", true);
    }

    private Step delayStep(Long ownerId, String code, int order) {
        return new Step(null, ownerId, code, code, StepType.DELAY, order, true,
                "{\"millis\":1}", "[]", "[]", false);
    }
}
