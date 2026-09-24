package com.workflowtest.engine.runtime;

import com.workflowtest.engine.support.EngineMessages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeVariableTargetTest {

    @Test
    void resolve_workflowScope_allowsBareAndWorkflowPrefix() {
        RuntimeVariableTarget.Resolved bare = RuntimeVariableTarget.resolve("orderId", RuntimeVariableScope.WORKFLOW);
        assertThat(bare.scope()).isEqualTo(RuntimeVariableScope.WORKFLOW);
        assertThat(bare.key()).isEqualTo("orderId");

        RuntimeVariableTarget.Resolved prefixed = RuntimeVariableTarget.resolve("workflow.orderId", RuntimeVariableScope.WORKFLOW);
        assertThat(prefixed.key()).isEqualTo("orderId");
    }

    @Test
    void resolve_workflowScope_rejectsGroupTarget() {
        assertThatThrownBy(() -> RuntimeVariableTarget.resolve("group.token", RuntimeVariableScope.WORKFLOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(EngineMessages.RUNTIME_VAR_WORKFLOW_TARGET_ONLY, "group.token"));
    }

    @Test
    void resolve_groupScope_allowsGroupTarget() {
        RuntimeVariableTarget.Resolved resolved = RuntimeVariableTarget.resolve("group.token", RuntimeVariableScope.GROUP);
        assertThat(resolved.scope()).isEqualTo(RuntimeVariableScope.GROUP);
        assertThat(resolved.key()).isEqualTo("token");
    }

    @Test
    void resolve_projectScope_allowsProjectTarget() {
        RuntimeVariableTarget.Resolved resolved = RuntimeVariableTarget.resolve("project.batchId", RuntimeVariableScope.PROJECT);
        assertThat(resolved.scope()).isEqualTo(RuntimeVariableScope.PROJECT);
        assertThat(resolved.key()).isEqualTo("batchId");
    }
}
