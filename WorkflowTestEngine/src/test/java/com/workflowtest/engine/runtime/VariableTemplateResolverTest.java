package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.support.EngineMessages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VariableTemplateResolverTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private VariableTemplateResolver resolver;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        resolver = new VariableTemplateResolver(objectMapper);
        EffectiveEnvironment environment = new EffectiveEnvironment(
                Map.of("baseUrl", "http://global"),
                Map.of("baseUrl", "http://project"),
                Map.of("baseUrl", "http://group", "token", "group-token"),
                Map.of("baseUrl", "http://workflow"),
                Map.of("baseUrl", "http://workflow"),
                Map.of());
        context = new ExecutionContext(environment, Map.of(), Map.of(), Map.of(), objectMapper);
        context.assignVariable("workflow.orderId", 12345, RuntimeVariableScope.WORKFLOW);
        context.assignVariable("workflow.enabled", true, RuntimeVariableScope.WORKFLOW);
        context.assignVariable("workflow.profile", Map.of("level", "vip", "score", 99), RuntimeVariableScope.WORKFLOW);
        context.assignVariable("group.channel", "mobile", RuntimeVariableScope.GROUP);
    }

    @Test
    void resolve_null_returnsNullNode() {
        JsonNode result = resolveAndPrint("null 节点", objectMapper.nullNode());
        assertThat(result.isNull()).isTrue();
    }

    @Test
    void resolve_exactPlaceholder_preservesOriginalType() throws Exception {
        JsonNode number = resolveAndPrint("整段数字占位符", text("${workflow.orderId}"));
        JsonNode bool = resolveAndPrint("整段布尔占位符", text("${workflow.enabled}"));
        JsonNode object = resolveAndPrint("整段对象占位符", text("${workflow.profile}"));

        assertThat(number.isNumber()).isTrue();
        assertThat(number.asInt()).isEqualTo(12345);
        assertThat(bool.isBoolean()).isTrue();
        assertThat(bool.asBoolean()).isTrue();
        assertThat(object.isObject()).isTrue();
        assertThat(object.path("level").asText()).isEqualTo("vip");
        assertThat(object.path("score").asInt()).isEqualTo(99);
    }

    @Test
    void resolve_mixedText_interpolatesAsString() throws Exception {
        JsonNode url = resolveAndPrint("字符串插值 URL", text("${workflow.baseUrl}/orders/${workflow.orderId}"));
        JsonNode label = resolveAndPrint("字符串插值标签", text("channel=${group.channel},seed=${group.seed}"));

        assertThat(url.isTextual()).isTrue();
        assertThat(url.asText()).isEqualTo("http://workflow/orders/12345");
        assertThat(label.asText()).isEqualTo("channel=mobile,seed=7");
    }

    @Test
    void resolve_objectAndArray_recursively() throws Exception {
        JsonNode source = objectMapper.readTree("""
                {
                  "url": "${workflow.baseUrl}/api",
                  "meta": {
                    "orderId": "${workflow.orderId}",
                    "enabled": "${workflow.enabled}"
                  },
                  "tags": ["${group.channel}", "fixed"]
                }
                """);
        JsonNode result = resolveAndPrint("对象与数组递归解析", source);

        assertThat(result.path("url").asText()).isEqualTo("http://workflow/api");
        assertThat(result.path("meta").path("orderId").asInt()).isEqualTo(12345);
        assertThat(result.path("meta").path("enabled").asBoolean()).isTrue();
        assertThat(result.path("tags").get(0).asText()).isEqualTo("mobile");
        assertThat(result.path("tags").get(1).asText()).isEqualTo("fixed");
    }

    @Test
    void resolve_nonTextualNode_isCopiedWithoutChange() throws Exception {
        JsonNode source = objectMapper.readTree("{\"count\":123,\"active\":true}");
        JsonNode result = resolveAndPrint("非文本节点原样复制", source);

        assertThat(result.path("count").asInt()).isEqualTo(123);
        assertThat(result.path("active").asBoolean()).isTrue();
    }

    @Test
    void resolve_missingVariable_throws() {
        assertThatThrownBy(() -> resolver.resolve(text("${workflow.missing}"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(String.format(EngineMessages.VARIABLE_NOT_FOUND, "workflow.missing"));

        System.out.println("[VariableTemplateResolver] 缺失变量抛出: "
                + String.format(EngineMessages.VARIABLE_NOT_FOUND, "workflow.missing"));
    }

    @Test
    void resolve_plainText_withoutPlaceholder_isUnchanged() throws Exception {
        JsonNode result = resolveAndPrint("无占位符纯文本", text("plain-value"));
        assertThat(result.asText()).isEqualTo("plain-value");
    }

    private JsonNode resolveAndPrint(String label, JsonNode source) {
        JsonNode result = resolver.resolve(source, context);
        printResult(label, source, result);
        return result;
    }

    private void printResult(String label, JsonNode source, JsonNode result) {
        try {
            System.out.println("======== VariableTemplateResolver#resolve ========");
            System.out.println("场景: " + label);
            System.out.println("输入: " + objectMapper.writeValueAsString(source));
            System.out.println("输出: " + objectMapper.writeValueAsString(result));
            System.out.println("输出类型: " + describeNodeType(result));
            System.out.println();
        } catch (Exception e) {
            throw new IllegalStateException("打印测试结果失败", e);
        }
    }

    private static String describeNodeType(JsonNode node) {
        if (node == null || node.isNull()) return "null";
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        return "unknown";
    }

    private JsonNode text(String value) {
        return objectMapper.getNodeFactory().textNode(value);
    }
}
