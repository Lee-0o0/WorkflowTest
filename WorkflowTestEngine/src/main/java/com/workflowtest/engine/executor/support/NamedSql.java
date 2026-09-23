package com.workflowtest.engine.executor.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NamedSql {
    private static final Pattern PARAM = Pattern.compile("(?<!:):([A-Za-z][A-Za-z0-9_]*)");
    private NamedSql() {}

    public static Parsed parse(String sql, Map<String, Object> parameters) {
        Matcher matcher = PARAM.matcher(sql);
        StringBuffer jdbcSql = new StringBuffer();
        List<Object> values = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!parameters.containsKey(name)) throw new IllegalArgumentException("缺少 SQL 参数: " + name);
            values.add(parameters.get(name));
            matcher.appendReplacement(jdbcSql, "?");
        }
        matcher.appendTail(jdbcSql);
        return new Parsed(jdbcSql.toString(), values);
    }

    public record Parsed(String sql, List<Object> values) {}
}
