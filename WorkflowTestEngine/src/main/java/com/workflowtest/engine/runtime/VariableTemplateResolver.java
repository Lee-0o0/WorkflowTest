package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.support.EngineMessages;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class VariableTemplateResolver {
    private static final String VARIABLE_PATTERN = "\\$\\{([^}]+)}";
    private static final Pattern VARIABLE = Pattern.compile(VARIABLE_PATTERN);

    private final ObjectMapper objectMapper;

    public JsonNode resolve(JsonNode source, ExecutionContext context) {
        if (source == null) {
            return objectMapper.nullNode();
        }
        if (source.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            fields.forEachRemaining(entry -> result.set(entry.getKey(), resolve(entry.getValue(), context)));
            return result;
        }
        if (source.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            source.forEach(node -> result.add(resolve(node, context)));
            return result;
        }
        if (!source.isTextual()) {
            return source.deepCopy();
        }
        String text = source.asText();
        // 整段文本就是一个占位符（如 "${workflow.orderId}"）：替换后保留变量原始类型（数字/布尔/对象/数组）。
        Matcher exact = VARIABLE.matcher(text);
        if (exact.matches()) {
            Object value = context.resolve(exact.group(1));
            if (value == null) {
                throw new IllegalArgumentException(String.format(EngineMessages.VARIABLE_NOT_FOUND, exact.group(1)));
            }
            return objectMapper.valueToTree(value);
        }
        // 文本中包含普通字符与占位符混合（如 "order-${workflow.orderId}-done"）：逐段替换，结果始终为字符串。
        Matcher matcher = VARIABLE.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object value = context.resolve(matcher.group(1));
            if (value == null) {
                throw new IllegalArgumentException(String.format(EngineMessages.VARIABLE_NOT_FOUND, matcher.group(1)));
            }
            // quoteReplacement 避免替换值里的 $、\ 被 appendReplacement 当作正则元字符。
            matcher.appendReplacement(output, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(output);
        return objectMapper.getNodeFactory().textNode(output.toString());
    }
}
