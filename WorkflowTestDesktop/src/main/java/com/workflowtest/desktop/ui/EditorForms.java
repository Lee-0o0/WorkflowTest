package com.workflowtest.desktop.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.engine.api.definition.DefinitionModels.ProjectResource;
import com.workflowtest.engine.api.definition.DefinitionModels.StepType;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

final class EditorForms {
    private EditorForms() {}

    static GridPane grid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(4, 0, 0, 0));
        applyFormColumns(grid);
        return grid;
    }

    static GridPane dialogGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        applyFormColumns(grid);
        return grid;
    }

    static void addRow(GridPane grid, int row, String label, Node control) {
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("form-label");
        grid.add(labelNode, 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }

    private static void applyFormColumns(GridPane grid) {
        ColumnConstraints labelColumn = new ColumnConstraints(88, 96, 140);
        ColumnConstraints fieldColumn = new ColumnConstraints();
        fieldColumn.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().setAll(labelColumn, fieldColumn);
    }

    static TextArea jsonArea(String text, int rows) {
        TextArea area = new TextArea(text == null ? "" : text);
        area.setPrefRowCount(rows);
        area.setWrapText(false);
        return area;
    }

    static String defaultConfig(StepType type) {
        if (type == null) return "{}";
        return switch (type) {
            case HTTP -> """
                    {
                      "method": "GET",
                      "url": "${project.baseUrl}/health",
                      "headers": {}
                    }""";
            case SQL -> """
                    {
                      "datasourceId": "",
                      "operation": "QUERY",
                      "sql": "SELECT 1 AS value",
                      "parameters": {}
                    }""";
            case DELAY -> """
                    {
                      "millis": 1000
                    }""";
        };
    }

    static String pretty(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return "";
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json));
        } catch (Exception e) {
            return json;
        }
    }

    static String json(ObjectMapper mapper, Object value) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return "null";
        }
    }

    static void applySqlConfig(ObjectMapper mapper, String configJson, ComboBox<ProjectResource> datasource,
                               ComboBox<String> operation, TextArea sql, TextArea parameters) {
        try {
            JsonNode node = mapper.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
            long datasourceId = node.path("datasourceId").isNumber()
                    ? node.path("datasourceId").asLong()
                    : parseLongOrZero(node.path("datasourceId").asText(""));
            datasource.getItems().stream()
                    .filter(resource -> resource.id() != null && resource.id() == datasourceId)
                    .findFirst()
                    .ifPresent(datasource::setValue);
            operation.setValue(node.path("operation").asText("QUERY"));
            sql.setText(node.path("sql").asText("SELECT 1 AS value"));
            JsonNode params = node.path("parameters");
            parameters.setText(params.isMissingNode() || params.isNull() ? "{}"
                    : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(params));
        } catch (Exception e) {
            operation.setValue("QUERY");
            sql.setText("SELECT 1 AS value");
            parameters.setText("{}");
        }
    }

    static String buildSqlConfigJson(ObjectMapper mapper, ProjectResource datasource, String operation,
                                     String sql, String parametersJson) {
        if (datasource == null || datasource.id() == null) throw new IllegalArgumentException("请选择数据源");
        if (sql == null || sql.isBlank()) throw new IllegalArgumentException("SQL 不能为空");
        try {
            ObjectNode config = mapper.createObjectNode();
            config.put("datasourceId", String.valueOf(datasource.id()));
            config.put("operation", operation == null || operation.isBlank() ? "QUERY" : operation);
            config.put("sql", sql.trim());
            config.set("parameters", mapper.readTree(
                    parametersJson == null || parametersJson.isBlank() ? "{}" : parametersJson));
            return mapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("参数 JSON 格式不正确", e);
        }
    }

    private static long parseLongOrZero(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
