package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class StepPostProcessor {
    private static final class Field {
        static final String TARGET = "target";
        static final String SOURCE = "source";
        static final String EXPRESSION = "expression";
        static final String OPERATOR = "operator";
        static final String EXPECTED = "expected";
        static final String REQUIRED = "required";
        static final String DEFAULT_VALUE = "defaultValue";
        static final String DEFAULT_SOURCE = Source.OUTPUT;
        static final String DEFAULT_OPERATOR = Operator.EQUALS;
    }

    private static final class Source {
        static final String OUTPUT = "OUTPUT";
        static final String RESPONSE_BODY = "RESPONSE_BODY";
        static final String RESPONSE_HEADER = "RESPONSE_HEADER";
        static final String STATUS_CODE = "STATUS_CODE";
        static final String SQL_ROWS = "SQL_ROWS";
        static final String UPDATE_COUNT = "UPDATE_COUNT";
    }

    private static final class JsonPathExpr {
        static final String RESPONSE_BODY = "$.response.body";
        static final String RESPONSE_HEADERS = "$.response.headers";
        static final String RESPONSE_STATUS = "$.response.status";
        static final String SQL_ROWS = "$.rows";
        static final String UPDATE_COUNT = "$.updateCount";
    }

    private static final class Operator {
        static final String EQUALS = "EQUALS";
        static final String NOT_EQUALS = "NOT_EQUALS";
        static final String CONTAINS = "CONTAINS";
        static final String NOT_CONTAINS = "NOT_CONTAINS";
        static final String EXISTS = "EXISTS";
        static final String NOT_NULL = "NOT_NULL";
        static final String NOT_EXISTS = "NOT_EXISTS";
        static final String IS_NULL = "IS_NULL";
        static final String GREATER_THAN = "GREATER_THAN";
        static final String GREATER_THAN_OR_EQUAL = "GREATER_THAN_OR_EQUAL";
        static final String LESS_THAN = "LESS_THAN";
        static final String LESS_THAN_OR_EQUAL = "LESS_THAN_OR_EQUAL";
        static final String MATCHES_REGEX = "MATCHES_REGEX";
        static final String SIZE_EQUALS = "SIZE_EQUALS";
    }

    private final ObjectMapper objectMapper;

    public JsonNode extract(JsonNode definitions, StepResult result, ExecutionContext context,
                            RuntimeVariableScope variableScope) {
        var extracted = objectMapper.createObjectNode();
        if (definitions == null || !definitions.isArray()) return extracted;
        Object document = objectMapper.convertValue(result.output(), Object.class);
        for (JsonNode definition : definitions) {
            String target = definition.path(Field.TARGET).asText();
            Object root = source(document, definition.path(Field.SOURCE).asText(Field.DEFAULT_SOURCE));
            String expression = definition.path(Field.EXPRESSION).asText();
            Object value = expression.isBlank() ? root : read(root, expression);
            if (value == null && definition.path(Field.REQUIRED).asBoolean(false)) {
                throw new AssertionError(String.format(EngineMessages.REQUIRED_EXTRACTION_MISSING, target));
            }
            if (value == null && definition.has(Field.DEFAULT_VALUE)) {
                value = objectMapper.convertValue(definition.get(Field.DEFAULT_VALUE), Object.class);
            }
            context.assignVariable(target, value, variableScope);
            RuntimeVariableTarget.Resolved resolved = RuntimeVariableTarget.resolve(target, variableScope);
            extracted.set(resolved.key(), objectMapper.valueToTree(value));
        }
        return extracted;
    }

    public JsonNode assertAll(JsonNode definitions, StepResult result) {
        var results = objectMapper.createArrayNode();
        if (definitions == null || !definitions.isArray()) return results;
        Object document = objectMapper.convertValue(result.output(), Object.class);
        List<String> failures = new ArrayList<>();
        for (JsonNode definition : definitions) {
            Object root = source(document, definition.path(Field.SOURCE).asText(Field.DEFAULT_SOURCE));
            String expression = definition.path(Field.EXPRESSION).asText();
            Object actual = expression.isBlank() ? root : read(root, expression);
            Object expected = definition.has(Field.EXPECTED) ? objectMapper.convertValue(definition.get(Field.EXPECTED), Object.class) : null;
            String operator = definition.path(Field.OPERATOR).asText(Field.DEFAULT_OPERATOR);
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

    private Object source(Object document, String source) {
        return switch (source) {
            case Source.RESPONSE_BODY -> read(document, JsonPathExpr.RESPONSE_BODY);
            case Source.RESPONSE_HEADER -> read(document, JsonPathExpr.RESPONSE_HEADERS);
            case Source.STATUS_CODE -> read(document, JsonPathExpr.RESPONSE_STATUS);
            case Source.SQL_ROWS -> read(document, JsonPathExpr.SQL_ROWS);
            case Source.UPDATE_COUNT -> read(document, JsonPathExpr.UPDATE_COUNT);
            default -> document;
        };
    }

    private Object read(Object root, String path) {
        if (root == null) return null;
        try { return JsonPath.read(root, path); } catch (Exception e) { return null; }
    }

    private boolean compare(String operator, Object actual, Object expected) {
        return switch (operator) {
            case Operator.EQUALS -> Objects.equals(normalize(actual), normalize(expected));
            case Operator.NOT_EQUALS -> !Objects.equals(normalize(actual), normalize(expected));
            case Operator.CONTAINS -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case Operator.NOT_CONTAINS -> actual == null || !String.valueOf(actual).contains(String.valueOf(expected));
            case Operator.EXISTS, Operator.NOT_NULL -> actual != null;
            case Operator.NOT_EXISTS, Operator.IS_NULL -> actual == null;
            case Operator.GREATER_THAN -> number(actual).compareTo(number(expected)) > 0;
            case Operator.GREATER_THAN_OR_EQUAL -> number(actual).compareTo(number(expected)) >= 0;
            case Operator.LESS_THAN -> number(actual).compareTo(number(expected)) < 0;
            case Operator.LESS_THAN_OR_EQUAL -> number(actual).compareTo(number(expected)) <= 0;
            case Operator.MATCHES_REGEX -> actual != null && Pattern.matches(String.valueOf(expected), String.valueOf(actual));
            case Operator.SIZE_EQUALS -> size(actual) == number(expected).intValue();
            default -> throw new IllegalArgumentException(String.format(EngineMessages.ASSERT_OPERATOR_UNSUPPORTED, operator));
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
