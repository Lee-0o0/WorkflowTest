package com.workflowtest.engine.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VariableTemplateResolver {
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    private final ObjectMapper objectMapper;

    public VariableTemplateResolver(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public JsonNode resolve(JsonNode source, ExecutionContext context) {
        if (source == null) return objectMapper.nullNode();
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
        if (!source.isTextual()) return source.deepCopy();
        String text = source.asText();
        Matcher exact = VARIABLE.matcher(text);
        if (exact.matches()) {
            Object value = context.resolve(exact.group(1));
            if (value == null) throw new IllegalArgumentException("变量不存在: " + exact.group(1));
            return objectMapper.valueToTree(value);
        }
        Matcher matcher = VARIABLE.matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            Object value = context.resolve(matcher.group(1));
            if (value == null) throw new IllegalArgumentException("变量不存在: " + matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(output);
        return objectMapper.getNodeFactory().textNode(output.toString());
    }
}
