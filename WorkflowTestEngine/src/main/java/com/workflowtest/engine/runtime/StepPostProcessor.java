package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StepPostProcessor {
    private final ObjectMapper objectMapper;

    public JsonNode extract(JsonNode definitions, StepResult result, ExecutionContext context, boolean groupScope) {
        var extracted = objectMapper.createObjectNode();
        if (definitions == null || !definitions.isArray()) return extracted;
        Object document = objectMapper.convertValue(result.output(), Object.class);
        for (JsonNode definition : definitions) {
            String target = definition.path("target").asText();
            Object root = source(document, definition.path("source").asText("OUTPUT"));
            String expression = definition.path("expression").asText();
            Object value = expression.isBlank() ? root : read(root, expression);
            if (value == null && definition.path("required").asBoolean(false))
                throw new AssertionError("必需提取值不存在: " + target);
            if (value == null && definition.has("defaultValue")) value = objectMapper.convertValue(definition.get("defaultValue"), Object.class);
            context.putVariable(target, value, groupScope);
            extracted.set(extractKey(target), objectMapper.valueToTree(value));
        }
        return extracted;
    }

    public JsonNode assertAll(JsonNode definitions, StepResult result) {
        var results = objectMapper.createArrayNode();
        if (definitions == null || !definitions.isArray()) return results;
        Object document = objectMapper.convertValue(result.output(), Object.class);
        List<String> failures = new ArrayList<>();
        for (JsonNode definition : definitions) {
            Object root = source(document, definition.path("source").asText("OUTPUT"));
            String expression = definition.path("expression").asText();
            Object actual = expression.isBlank() ? root : read(root, expression);
            Object expected = definition.has("expected") ? objectMapper.convertValue(definition.get("expected"), Object.class) : null;
            String operator = definition.path("operator").asText("EQUALS");
            boolean passed = compare(operator, actual, expected);
            var item = objectMapper.createObjectNode();
            item.put("passed", passed); item.put("operator", operator); item.put("expression", expression);
            item.set("actual", objectMapper.valueToTree(actual)); item.set("expected", objectMapper.valueToTree(expected));
            results.add(item);
            if (!passed) failures.add(expression + " " + operator + "，期望=" + expected + "，实际=" + actual);
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
        return results;
    }

    private String extractKey(String target) {
        if (target.startsWith("group.")) return target.substring(6);
        if (target.startsWith("workflow.")) return target.substring(9);
        return target;
    }

    private Object source(Object document, String source) {
        return switch (source) {
            case "RESPONSE_BODY" -> read(document, "$.response.body");
            case "RESPONSE_HEADER" -> read(document, "$.response.headers");
            case "STATUS_CODE" -> read(document, "$.response.status");
            case "SQL_ROWS" -> read(document, "$.rows");
            case "UPDATE_COUNT" -> read(document, "$.updateCount");
            default -> document;
        };
    }

    private Object read(Object root, String path) {
        if (root == null) return null;
        try { return JsonPath.read(root, path); } catch (Exception e) { return null; }
    }

    private boolean compare(String operator, Object actual, Object expected) {
        return switch (operator) {
            case "EQUALS" -> Objects.equals(normalize(actual), normalize(expected));
            case "NOT_EQUALS" -> !Objects.equals(normalize(actual), normalize(expected));
            case "CONTAINS" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "NOT_CONTAINS" -> actual == null || !String.valueOf(actual).contains(String.valueOf(expected));
            case "EXISTS", "NOT_NULL" -> actual != null;
            case "NOT_EXISTS", "IS_NULL" -> actual == null;
            case "GREATER_THAN" -> number(actual).compareTo(number(expected)) > 0;
            case "GREATER_THAN_OR_EQUAL" -> number(actual).compareTo(number(expected)) >= 0;
            case "LESS_THAN" -> number(actual).compareTo(number(expected)) < 0;
            case "LESS_THAN_OR_EQUAL" -> number(actual).compareTo(number(expected)) <= 0;
            case "MATCHES_REGEX" -> actual != null && Pattern.matches(String.valueOf(expected), String.valueOf(actual));
            case "SIZE_EQUALS" -> size(actual) == number(expected).intValue();
            default -> throw new IllegalArgumentException("不支持的断言操作符: " + operator);
        };
    }

    private Object normalize(Object value) { return value instanceof Number ? new BigDecimal(value.toString()) : value; }
    private BigDecimal number(Object value) { return new BigDecimal(String.valueOf(value)); }
    private int size(Object value) {
        if (value instanceof java.util.Collection<?> c) return c.size();
        if (value instanceof java.util.Map<?, ?> m) return m.size();
        return value == null ? 0 : String.valueOf(value).length();
    }
}
