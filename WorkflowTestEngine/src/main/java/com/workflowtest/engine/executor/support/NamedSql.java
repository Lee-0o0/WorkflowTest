package com.workflowtest.engine.executor.support;

import com.workflowtest.engine.runtime.ExecutionContext;
import com.workflowtest.engine.support.EngineMessages;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NamedSql {
    private static final Pattern PARAM = Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)");
    private NamedSql() {}

    public static Parsed parse(String sql, ExecutionContext context) {
        return parse(sql, name -> {
            Object value = context.resolve(name);
            if (value == null) {
                throw new IllegalArgumentException(String.format(EngineMessages.SQL_PARAM_NOT_FOUND, name));
            }
            return value;
        });
    }

    public static Parsed parse(String sql, Function<String, Object> parameterResolver) {
        Matcher matcher = PARAM.matcher(sql);
        StringBuffer jdbcSql = new StringBuffer();
        List<Object> values = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            values.add(parameterResolver.apply(name));
            matcher.appendReplacement(jdbcSql, "?");
        }
        matcher.appendTail(jdbcSql);
        return new Parsed(jdbcSql.toString(), values);
    }

    public record Parsed(String sql, List<Object> values) {}
}
