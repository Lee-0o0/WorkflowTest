package com.workflowtest.engine.executor.support;

import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.runtime.RuntimeVariableScope;
import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.support.EngineMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamedSqlTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of(), Map.of(), Map.of(),
                Map.of("status", "WAIT_PAY"),
                Map.of("status", "WAIT_PAY"),
                Map.of());
        context = new ExecutionContext(environment, Map.of(), Map.of(), Map.of("orderId", 42), objectMapper);
        context.assignVariable("workflow.orderId", 99, RuntimeVariableScope.WORKFLOW);
    }

    @Test
    void parse_resolvesParametersFromEnvironment() {
        NamedSql.Parsed parsed = NamedSql.parse(
                "SELECT id FROM orders WHERE status = :status AND id = :orderId", context);

        assertThat(parsed.sql()).isEqualTo("SELECT id FROM orders WHERE status = ? AND id = ?");
        assertThat(parsed.values()).containsExactly("WAIT_PAY", 99);
    }

    @Test
    void parse_missingParameterFails() {
        assertThatThrownBy(() -> NamedSql.parse("SELECT * FROM t WHERE x = :missing", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(EngineMessages.SQL_PARAM_NOT_FOUND, "missing"));
    }
}
