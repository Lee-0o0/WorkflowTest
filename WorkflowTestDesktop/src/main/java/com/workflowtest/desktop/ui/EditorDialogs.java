package com.workflowtest.desktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.DefinitionModels.*;
import com.workflowtest.engine.api.DefinitionService;
import com.workflowtest.engine.api.ExecutionQueryService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Optional;
import java.util.List;

final class EditorDialogs {
    private EditorDialogs() {}

    static Optional<String[]> nameDialog(String title, String initialName, String initialDescription) {
        Dialog<String[]> dialog = base(title);
        TextField name = new TextField(initialName == null ? "" : initialName);
        TextArea description = new TextArea(initialDescription == null ? "" : initialDescription);
        description.setPrefRowCount(3);
        GridPane grid = grid();
        grid.addRow(0, new Label("名称"), name);
        grid.addRow(1, new Label("说明"), description);
        dialog.getDialogPane().setContent(grid);
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(name.textProperty().isEmpty());
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new String[]{name.getText().trim(), description.getText().trim()} : null);
        return dialog.showAndWait();
    }

    static Optional<Step> stepDialog(String ownerId, Step current, boolean hookStep) {
        Dialog<Step> dialog = base(current == null ? "新增步骤" : "编辑步骤");
        TextField code = new TextField(current == null ? "step" : current.code());
        TextField name = new TextField(current == null ? "新步骤" : current.name());
        ComboBox<StepType> type = new ComboBox<>();
        type.getItems().setAll(StepType.values());
        type.setValue(current == null ? StepType.HTTP : current.type());
        Spinner<Integer> order = new Spinner<>(0, 9999, current == null ? 0 : current.sortOrder());
        TextArea config = area(current == null ? defaultConfig(type.getValue()) : current.configJson(), 8);
        TextArea extraction = area(current == null ? "[]" : current.extractionJson(), 4);
        TextArea assertion = area(current == null ? "[]" : current.assertionJson(), 4);
        type.valueProperty().addListener((obs, old, value) -> {
            if (current == null && config.getText().equals(defaultConfig(old))) config.setText(defaultConfig(value));
        });
        GridPane grid = grid();
        grid.addRow(0, new Label("编码"), code);
        grid.addRow(1, new Label("名称"), name);
        grid.addRow(2, new Label("类型"), type);
        grid.addRow(3, new Label("顺序"), order);
        grid.addRow(4, new Label("配置 JSON"), config);
        grid.addRow(5, new Label("提取 JSON"), extraction);
        grid.addRow(6, new Label("断言 JSON"), assertion);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefSize(760, 680);
        dialog.setResultConverter(button -> button == ButtonType.OK ? new Step(
                current == null ? null : current.id(), ownerId, code.getText().trim(), name.getText().trim(),
                type.getValue(), order.getValue(), true, config.getText(), extraction.getText(), assertion.getText(),
                FailureStrategy.STOP, "{}", hookStep) : null);
        return dialog.showAndWait();
    }

    static Optional<ScopedVariable> variableDialog(ScopeType type, String scopeId,
                                                    ScopedVariable current, ObjectMapper mapper) {
        Dialog<ScopedVariable> dialog = base(current == null ? "新增环境变量" : "编辑环境变量");
        TextField key = new TextField(current == null ? "" : current.key());
        TextArea value = area(current == null ? "\"value\"" : json(mapper, current.value()), 4);
        CheckBox sensitive = new CheckBox("敏感变量");
        sensitive.setSelected(current != null && current.sensitive());
        GridPane grid = grid();
        grid.addRow(0, new Label("变量名"), key);
        grid.addRow(1, new Label("JSON 值"), value);
        grid.addRow(2, new Label("安全"), sensitive);
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            try {
                Object parsed = mapper.readValue(value.getText(), Object.class);
                return new ScopedVariable(current == null ? null : current.id(), type, scopeId,
                        key.getText().trim(), "AUTO", parsed, sensitive.isSelected(), true);
            } catch (Exception e) {
                showError("变量值必须是合法 JSON，例如 \"text\"、123、true、{} 或 []");
                return null;
            }
        });
        return dialog.showAndWait();
    }

    static Optional<DataSourceInput> dataSourceDialog(String projectId, RuntimeDataSource current) {
        Dialog<DataSourceInput> dialog = base(current == null ? "新增数据源" : "编辑数据源");
        TextField name = new TextField(current == null ? "测试数据库" : current.name());
        TextField driver = new TextField(current == null ? "org.sqlite.JDBC" : current.driverClass());
        TextField url = new TextField(current == null ? "jdbc:sqlite:C:/temp/test.db" : current.jdbcUrl());
        TextField username = new TextField(current == null ? "" : current.username());
        PasswordField password = new PasswordField();
        CheckBox dangerous = new CheckBox("允许 DDL 危险 SQL");
        dangerous.setSelected(current != null && current.allowDangerousSql());
        GridPane grid = grid();
        grid.addRow(0, new Label("名称"), name); grid.addRow(1, new Label("驱动"), driver);
        grid.addRow(2, new Label("JDBC URL"), url); grid.addRow(3, new Label("用户名"), username);
        grid.addRow(4, new Label("密码"), password); grid.addRow(5, new Label("权限"), dangerous);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(680);
        dialog.setResultConverter(button -> button == ButtonType.OK ? new DataSourceInput(
                new RuntimeDataSource(current == null ? null : current.id(), projectId, name.getText().trim(),
                        driver.getText().trim(), url.getText().trim(), username.getText().trim(),
                        dangerous.isSelected(), true), password.getText()) : null);
        return dialog.showAndWait();
    }

    record DataSourceInput(RuntimeDataSource source, String password) {}

    static void manageVariables(ScopeType type, String scopeId, DefinitionService definitions, ObjectMapper mapper) {
        Dialog<Void> dialog = base("环境变量管理");
        ListView<ScopedVariable> list = new ListView<>();
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(ScopedVariable item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.key() + " = "
                        + (item.sensitive() ? "******" : json(mapper, item.value())));
            }
        });
        Runnable reload = () -> list.setItems(FXCollections.observableArrayList(definitions.listVariables(type, scopeId)));
        Button add = new Button("新增");
        Button edit = new Button("编辑");
        Button delete = new Button("删除");
        add.setOnAction(e -> variableDialog(type, scopeId, null, mapper).ifPresent(v -> { definitions.saveVariable(v); reload.run(); }));
        edit.setOnAction(e -> {
            ScopedVariable selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) variableDialog(type, scopeId, selected, mapper).ifPresent(v -> { definitions.saveVariable(v); reload.run(); });
        });
        delete.setOnAction(e -> {
            ScopedVariable selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && confirm("确认删除变量“" + selected.key() + "”？")) { definitions.deleteVariable(selected.id()); reload.run(); }
        });
        VBox box = new VBox(10, list, new ToolBar(add, edit, delete));
        box.setPadding(new Insets(10)); list.setPrefSize(560, 360);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().remove(ButtonType.OK);
        reload.run(); dialog.showAndWait();
    }

    static void manageDataSources(String projectId, DefinitionService definitions) {
        Dialog<Void> dialog = base("数据源管理");
        ListView<RuntimeDataSource> list = new ListView<>();
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(RuntimeDataSource item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + "  —  " + item.jdbcUrl());
            }
        });
        Runnable reload = () -> list.setItems(FXCollections.observableArrayList(definitions.listDataSources(projectId)));
        Button add = new Button("新增"); Button edit = new Button("编辑");
        Button test = new Button("测试连接"); Button delete = new Button("删除");
        add.setOnAction(e -> dataSourceDialog(projectId, null).ifPresent(v -> { definitions.saveDataSource(v.source(), v.password()); reload.run(); }));
        edit.setOnAction(e -> {
            RuntimeDataSource selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) dataSourceDialog(projectId, selected).ifPresent(v -> { definitions.saveDataSource(v.source(), v.password()); reload.run(); });
        });
        test.setOnAction(e -> {
            RuntimeDataSource selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try { definitions.testDataSource(selected.id()); showInfo("连接测试", "连接成功"); }
                catch (Exception ex) { showError(ex.getMessage()); }
            }
        });
        delete.setOnAction(e -> {
            RuntimeDataSource selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && confirm("确认删除数据源“" + selected.name() + "”？")) { definitions.deleteDataSource(selected.id()); reload.run(); }
        });
        VBox box = new VBox(10, list, new ToolBar(add, edit, test, delete));
        box.setPadding(new Insets(10)); list.setPrefSize(720, 360);
        dialog.getDialogPane().setContent(box); dialog.getDialogPane().getButtonTypes().remove(ButtonType.OK);
        reload.run(); dialog.showAndWait();
    }

    static void executionDetails(ExecutionQueryService.ExecutionSummary summary,
                                 List<ExecutionQueryService.StepExecutionDetail> steps, ObjectMapper mapper) {
        Dialog<Void> dialog = base("执行详情 - " + summary.targetName());
        TableView<ExecutionQueryService.StepExecutionDetail> table = new TableView<>();
        table.getColumns().addAll(detailColumn("阶段", ExecutionQueryService.StepExecutionDetail::phase),
                detailColumn("步骤", ExecutionQueryService.StepExecutionDetail::stepCode),
                detailColumn("状态", ExecutionQueryService.StepExecutionDetail::status),
                detailColumn("耗时(ms)", s -> String.valueOf(s.elapsedMs())),
                detailColumn("错误", s -> s.errorMessage() == null ? "" : s.errorMessage()));
        table.setItems(FXCollections.observableArrayList(steps));
        TextArea content = new TextArea("双击步骤查看请求、响应、输出、提取和断言");
        content.setEditable(false); content.setPrefRowCount(18);
        table.setRowFactory(view -> {
            TableRow<ExecutionQueryService.StepExecutionDetail> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    var s = row.getItem();
                    content.setText("请求\n" + pretty(mapper, s.requestJson()) + "\n\n响应\n" + pretty(mapper, s.responseJson())
                            + "\n\n输出\n" + pretty(mapper, s.outputJson()) + "\n\n提取\n" + pretty(mapper, s.extractedJson())
                            + "\n\n断言\n" + pretty(mapper, s.assertionJson()));
                }
            }); return row;
        });
        SplitPane split = new SplitPane(table, content); split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.45); dialog.getDialogPane().setContent(split);
        dialog.getDialogPane().setPrefSize(920, 700); dialog.getDialogPane().getButtonTypes().remove(ButtonType.OK);
        dialog.showAndWait();
    }

    private static TableColumn<ExecutionQueryService.StepExecutionDetail, String> detailColumn(
            String title, java.util.function.Function<ExecutionQueryService.StepExecutionDetail, String> value) {
        TableColumn<ExecutionQueryService.StepExecutionDetail, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private static String pretty(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) return "";
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readTree(json)); }
        catch (Exception e) { return json; }
    }

    static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("操作失败"); alert.showAndWait();
    }

    static void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(title); alert.showAndWait();
    }

    static boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static <T> Dialog<T> base(String title) {
        Dialog<T> dialog = new Dialog<>(); dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        return dialog;
    }
    private static GridPane grid() {
        GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(15));
        GridPane.setHgrow(grid, Priority.ALWAYS); return grid;
    }
    private static TextArea area(String text, int rows) {
        TextArea area = new TextArea(text == null ? "" : text); area.setPrefRowCount(rows); area.setWrapText(false);
        GridPane.setHgrow(area, Priority.ALWAYS); return area;
    }
    private static String defaultConfig(StepType type) {
        if (type == null) return "{}";
        return switch (type) {
            case HTTP -> "{\n  \"method\": \"GET\",\n  \"url\": \"${env.baseUrl}/health\",\n  \"headers\": {},\n  \"readTimeoutMs\": 10000\n}";
            case SQL -> "{\n  \"datasourceId\": \"\",\n  \"operation\": \"QUERY\",\n  \"sql\": \"SELECT 1 AS value\",\n  \"parameters\": {}\n}";
            case DELAY -> "{\n  \"millis\": 1000\n}";
        };
    }
    private static String json(ObjectMapper mapper, Object value) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception e) { return "null"; }
    }
}
