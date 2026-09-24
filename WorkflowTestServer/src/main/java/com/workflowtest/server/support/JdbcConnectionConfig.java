package com.workflowtest.server.support;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JdbcConnectionConfig {
    private static final Pattern STANDARD_URL = Pattern.compile(
            "^jdbc:(\\w+)://([^:/]+)(?::(\\d+))?/([^?;]+)(?:\\?(.*))?$");
    private static final Pattern SQLSERVER_URL = Pattern.compile(
            "^jdbc:sqlserver://([^:;]+)(?::(\\d+))?;(?:.*?)databaseName=([^;]+)(?:;(.*))?$", Pattern.CASE_INSENSITIVE);

    private JdbcConnectionConfig() {}

    public record DriverOption(String label, String driverClass, int defaultPort, String vendor) {}

    public static final List<DriverOption> DRIVERS = List.of(
            new DriverOption("MySQL", "com.mysql.cj.jdbc.Driver", 3306, "mysql"),
            new DriverOption("PostgreSQL", "org.postgresql.Driver", 5432, "postgresql"),
            new DriverOption("SQLite", "org.sqlite.JDBC", 0, "sqlite"),
            new DriverOption("SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver", 1433, "sqlserver"),
            new DriverOption("Oracle", "oracle.jdbc.OracleDriver", 1521, "oracle")
    );

    public static DriverOption findDriver(String driverClass) {
        if (driverClass == null || driverClass.isBlank()) return DRIVERS.getFirst();
        return DRIVERS.stream()
                .filter(option -> option.driverClass().equals(driverClass))
                .findFirst()
                .orElse(new DriverOption("自定义", driverClass, 3306, guessVendor(driverClass)));
    }

    public static void normalizeDatasourceConfig(Map<String, Object> config) {
        enrichFromJdbcUrl(config);
        if (string(config, "driverClass").isBlank()) {
            throw new IllegalArgumentException("JDBC 驱动不能为空");
        }
        String jdbcUrl = buildJdbcUrl(config);
        if (jdbcUrl.isBlank()) throw new IllegalArgumentException("JDBC 连接信息不完整");
        config.put("jdbcUrl", jdbcUrl);
    }

    public static String resolveJdbcUrl(Map<String, Object> config) {
        enrichFromJdbcUrl(config);
        if (!string(config, "host").isBlank() || !string(config, "database").isBlank()) {
            return buildJdbcUrl(config);
        }
        return string(config, "jdbcUrl");
    }

    public static String buildJdbcUrl(Map<String, Object> config) {
        DriverOption driver = findDriver(string(config, "driverClass"));
        String host = string(config, "host");
        String port = string(config, "port");
        String database = string(config, "database");
        String params = string(config, "urlParams");

        if ("sqlite".equals(driver.vendor())) {
            if (database.isBlank()) return string(config, "jdbcUrl");
            return "jdbc:sqlite:" + database;
        }
        if (host.isBlank() || database.isBlank()) return string(config, "jdbcUrl");

        int portNumber = port.isBlank() ? driver.defaultPort() : Integer.parseInt(port);
        return switch (driver.vendor()) {
            case "mysql" -> appendQuery("jdbc:mysql://" + host + ":" + portNumber + "/" + database, params);
            case "postgresql" -> appendQuery("jdbc:postgresql://" + host + ":" + portNumber + "/" + database, params);
            case "oracle" -> appendQuery("jdbc:oracle:thin:@" + host + ":" + portNumber + ":" + database, params);
            case "sqlserver" -> {
                String base = "jdbc:sqlserver://" + host + ":" + portNumber + ";databaseName=" + database;
                yield params.isBlank() ? base : base + ";" + params.replace("&", ";");
            }
            default -> appendQuery("jdbc:" + driver.vendor() + "://" + host + ":" + portNumber + "/" + database, params);
        };
    }

    public static void enrichFromJdbcUrl(Map<String, Object> config) {
        if (!string(config, "host").isBlank() || !string(config, "database").isBlank()) return;
        String url = string(config, "jdbcUrl");
        if (url.isBlank()) return;

        if (url.startsWith("jdbc:sqlite:")) {
            config.putIfAbsent("database", url.substring("jdbc:sqlite:".length()));
            config.putIfAbsent("driverClass", "org.sqlite.JDBC");
            return;
        }

        Matcher sqlServer = SQLSERVER_URL.matcher(url);
        if (sqlServer.matches()) {
            config.putIfAbsent("host", sqlServer.group(1));
            if (sqlServer.group(2) != null) config.putIfAbsent("port", sqlServer.group(2));
            config.putIfAbsent("database", decode(sqlServer.group(3)));
            if (sqlServer.group(4) != null) config.putIfAbsent("urlParams", sqlServer.group(4));
            config.putIfAbsent("driverClass", "com.microsoft.sqlserver.jdbc.SQLServerDriver");
            return;
        }

        Matcher standard = STANDARD_URL.matcher(url);
        if (standard.matches()) {
            config.putIfAbsent("host", standard.group(2));
            if (standard.group(3) != null) config.putIfAbsent("port", standard.group(3));
            config.putIfAbsent("database", decode(standard.group(4)));
            if (standard.group(5) != null) config.putIfAbsent("urlParams", standard.group(5));
            config.putIfAbsent("driverClass", defaultDriverForVendor(standard.group(1)));
        }
    }

    private static String defaultDriverForVendor(String vendor) {
        return DRIVERS.stream()
                .filter(option -> option.vendor().equalsIgnoreCase(vendor))
                .map(DriverOption::driverClass)
                .findFirst()
                .orElse("");
    }

    private static String guessVendor(String driverClass) {
        String lower = driverClass.toLowerCase(Locale.ROOT);
        if (lower.contains("sqlite")) return "sqlite";
        if (lower.contains("postgres")) return "postgresql";
        if (lower.contains("sqlserver")) return "sqlserver";
        if (lower.contains("oracle")) return "oracle";
        return "mysql";
    }

    private static String appendQuery(String base, String params) {
        if (params == null || params.isBlank()) return base;
        return base + "?" + params;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String string(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
