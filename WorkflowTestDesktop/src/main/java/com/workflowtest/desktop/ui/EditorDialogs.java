package com.workflowtest.desktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.execution.ExecutionModels.ExecutionSummary;
import com.workflowtest.engine.api.execution.ExecutionModels.StepExecutionDetail;
import com.workflowtest.engine.api.definition.ProjectResourceService;
import com.workflowtest.engine.api.definition.ScopedVariableService;
import com.workflowtest.engine.support.JdbcConnectionConfig;
import com.workflowtest.engine.support.JdbcConnectionConfig.DriverOption;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.event.ActionEvent;
import javafx.event.ActionEvent;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

final class EditorDialogs {
    private EditorDialogs() {}

    static Optional<String[]> nameDialog(String title, String initialName, String initialDescription) {
        Dialog<String[]> dialog = base(title);
        TextField name = new TextField(initialName == null ? "" : initialName);
        TextArea description = new TextArea(initialDescription == null ? "" : initialDescription);
        description.setPrefRowCount(3);
        GridPane grid = EditorForms.dialogGrid();
        EditorForms.addRow(grid, 0, "名称", name);
        EditorForms.addRow(grid, 1, "说明", description);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(480);
        Node ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(name.textProperty().isEmpty());
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new String[]{name.getText().trim(), description.getText().trim()} : null);
        return dialog.showAndWait();
    }

    static Optional<Step> stepDialog(Long ownerId, Step current, boolean hookStep) {
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
        GridPane grid = EditorForms.dialogGrid();
        EditorForms.addRow(grid, 0, "编码", code);
        EditorForms.addRow(grid, 1, "名称", name);
        EditorForms.addRow(grid, 2, "类型", type);
        EditorForms.addRow(grid, 3, "顺序", order);
        EditorForms.addRow(grid, 4, "配置 JSON", config);
        EditorForms.addRow(grid, 5, "提取 JSON", extraction);
        EditorForms.addRow(grid, 6, "断言 JSON", assertion);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefSize(760, 680);
        dialog.getDialogPane().setMinWidth(640);
        dialog.setResultConverter(button -> button == ButtonType.OK ? new Step(
                current == null ? null : current.id(), ownerId, code.getText().trim(), name.getText().trim(),
                type.getValue(), order.getValue(), true, config.getText(), extraction.getText(), assertion.getText(),
                hookStep) : null);
        return dialog.showAndWait();
    }

    static Optional<ScopedVariable> variableDialog(ScopeType type, Long scopeId,
                                                    ScopedVariable current, ObjectMapper mapper) {
        Dialog<ScopedVariable> dialog = base(current == null ? "新增环境变量" : "编辑环境变量");
        TextField key = new TextField(current == null ? "" : current.key());
        TextArea value = area(current == null ? "\"value\"" : json(mapper, current.value()), 4);
        GridPane grid = EditorForms.dialogGrid();
        EditorForms.addRow(grid, 0, "变量名", key);
        EditorForms.addRow(grid, 1, "JSON 值", value);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(520);
        key.setPrefWidth(360);
        value.setPrefWidth(360);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            try {
                Object parsed = mapper.readValue(value.getText(), Object.class);
                return new ScopedVariable(current == null ? null : current.id(), type, scopeId,
                        key.getText().trim(), "AUTO", parsed, true);
            } catch (Exception e) {
                showError("变量值必须是合法 JSON，例如 \"text\"、123、true、{} 或 []");
                return null;
            }
        });
        return dialog.showAndWait();
    }

    static Optional<GlobalVariable> globalVariableDialog(GlobalVariable current, ObjectMapper mapper) {
        Dialog<GlobalVariable> dialog = base(current == null ? "新增全局环境变量" : "编辑全局环境变量");
        TextField key = new TextField(current == null ? "" : current.key());
        TextArea value = area(current == null ? "\"value\"" : json(mapper, current.value()), 4);
        GridPane grid = EditorForms.dialogGrid();
        EditorForms.addRow(grid, 0, "变量名", key);
        EditorForms.addRow(grid, 1, "JSON 值", value);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(520);
        key.setPrefWidth(360);
        value.setPrefWidth(360);
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            try {
                Object parsed = mapper.readValue(value.getText(), Object.class);
                return new GlobalVariable(current == null ? null : current.id(),
                        key.getText().trim(), "AUTO", parsed, true);
            } catch (Exception e) {
                showError("变量值必须是合法 JSON，例如 \"text\"、123、true、{} 或 []");
                return null;
            }
        });
        return dialog.showAndWait();
    }

    static Optional<ProjectResourceInput> projectResourceDialog(Long projectId, ProjectResource current,
                                                                ProjectResourceService projectResources) {
        boolean creating = current == null;
        ProjectResourceType initialType = creating ? ProjectResourceType.DATASOURCE : current.type();
        Dialog<ProjectResourceInput> dialog = base(creating ? "新增项目资源" : "编辑项目资源");

        ComboBox<ProjectResourceType> type = new ComboBox<>();
        type.getItems().setAll(ProjectResourceType.values());
        type.setValue(initialType);
        type.setDisable(!creating);
        type.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ProjectResourceType value) {
                return value == null ? "" : switch (value) {
                    case DATASOURCE -> "数据源";
                    case FILE -> "文件";
                };
            }
            @Override public ProjectResourceType fromString(String string) { return null; }
        });

        TextField name = new TextField(creating ? "" : current.name());

        ComboBox<DriverOption> driver = new ComboBox<>();
        driver.getItems().setAll(JdbcConnectionConfig.DRIVERS);
        driver.setValue(JdbcConnectionConfig.DRIVERS.getFirst());
        driver.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(DriverOption value) { return value == null ? "" : value.label(); }
            @Override public DriverOption fromString(String string) { return null; }
        });
        TextField host = new TextField("127.0.0.1");
        TextField port = new TextField("3306");
        port.setPrefWidth(90);
        HBox hostPortRow = new HBox(8, host, port);
        HBox.setHgrow(host, Priority.ALWAYS);
        TextField database = new TextField();
        Button dbBrowse = new Button("浏览...");
        dbBrowse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择 SQLite 数据库文件");
            File file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) database.setText(file.getAbsolutePath());
        });
        HBox databaseRow = new HBox(8, database, dbBrowse);
        HBox.setHgrow(database, Priority.ALWAYS);
        TextField urlParams = new TextField();
        Label urlParamsHint = new Label("示例：charset=utf8mb4&serverTimezone=Asia/Shanghai");
        urlParamsHint.getStyleClass().add("hint-label");
        urlParamsHint.setWrapText(true);
        TextField jdbcPreview = new TextField();
        jdbcPreview.setEditable(false);
        jdbcPreview.getStyleClass().add("jdbc-url-preview");
        TextField username = new TextField();
        PasswordField password = new PasswordField();
        CheckBox dangerous = new CheckBox("允许 DDL 危险 SQL");

        Runnable refreshJdbcPreview = () -> {
            Map<String, Object> preview = new LinkedHashMap<>();
            DriverOption selected = driver.getValue();
            if (selected != null) preview.put("driverClass", selected.driverClass());
            preview.put("host", host.getText().trim());
            preview.put("port", port.getText().trim());
            preview.put("database", database.getText().trim());
            preview.put("urlParams", urlParams.getText().trim());
            jdbcPreview.setText(JdbcConnectionConfig.buildJdbcUrl(preview));
        };
        Runnable syncDriverDefaults = () -> {
            DriverOption selected = driver.getValue();
            if (selected == null) return;
            boolean sqlite = "sqlite".equals(selected.vendor());
            host.setDisable(sqlite);
            port.setDisable(sqlite);
            dbBrowse.setVisible(sqlite);
            dbBrowse.setManaged(sqlite);
            if (sqlite) {
                host.clear();
                port.clear();
            } else if (port.getText().isBlank() && selected.defaultPort() > 0) {
                port.setText(String.valueOf(selected.defaultPort()));
            }
            refreshJdbcPreview.run();
        };
        driver.valueProperty().addListener((obs, old, value) -> syncDriverDefaults.run());
        host.textProperty().addListener((obs, old, value) -> refreshJdbcPreview.run());
        port.textProperty().addListener((obs, old, value) -> refreshJdbcPreview.run());
        database.textProperty().addListener((obs, old, value) -> refreshJdbcPreview.run());
        urlParams.textProperty().addListener((obs, old, value) -> refreshJdbcPreview.run());

        TextField filePath = new TextField();
        Button browse = new Button("浏览...");
        browse.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("选择文件");
            File file = chooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
            if (file != null) filePath.setText(file.getAbsolutePath());
        });
        HBox pathRow = new HBox(8, filePath, browse);
        HBox.setHgrow(filePath, Priority.ALWAYS);
        TextField encoding = new TextField("UTF-8");
        TextArea fileDescription = new TextArea();
        fileDescription.setPrefRowCount(3);

        GridPane datasourceGrid = EditorForms.dialogGrid();
        EditorForms.addRow(datasourceGrid, 0, "JDBC 驱动", driver);
        EditorForms.addRow(datasourceGrid, 1, "连接 URL", jdbcPreview);
        EditorForms.addRow(datasourceGrid, 2, "主机", hostPortRow);
        EditorForms.addRow(datasourceGrid, 3, "数据库", databaseRow);
        EditorForms.addRow(datasourceGrid, 4, "URL 参数", urlParams);
        EditorForms.addRow(datasourceGrid, 5, "", urlParamsHint);
        EditorForms.addRow(datasourceGrid, 6, "用户名", username);
        EditorForms.addRow(datasourceGrid, 7, "密码", password);
        EditorForms.addRow(datasourceGrid, 8, "权限", dangerous);

        GridPane fileGrid = EditorForms.dialogGrid();
        EditorForms.addRow(fileGrid, 0, "文件路径", pathRow);
        EditorForms.addRow(fileGrid, 1, "编码", encoding);
        EditorForms.addRow(fileGrid, 2, "说明", fileDescription);

        StackPane typeForms = new StackPane(datasourceGrid, fileGrid);
        Runnable syncTypeForms = () -> {
            boolean datasource = type.getValue() == ProjectResourceType.DATASOURCE;
            datasourceGrid.setVisible(datasource);
            datasourceGrid.setManaged(datasource);
            fileGrid.setVisible(!datasource);
            fileGrid.setManaged(!datasource);
        };
        type.valueProperty().addListener((obs, old, value) -> syncTypeForms.run());

        if (!creating) {
            Map<String, Object> config = current.config();
            if (current.type() == ProjectResourceType.DATASOURCE) {
                Map<String, Object> enriched = new LinkedHashMap<>(config);
                JdbcConnectionConfig.enrichFromJdbcUrl(enriched);
                driver.setValue(JdbcConnectionConfig.findDriver(str(enriched, "driverClass")));
                host.setText(str(enriched, "host", "127.0.0.1"));
                port.setText(str(enriched, "port"));
                database.setText(str(enriched, "database"));
                urlParams.setText(str(enriched, "urlParams"));
                username.setText(str(enriched, "username"));
                dangerous.setSelected(bool(enriched, "allowDangerousSql"));
            } else {
                filePath.setText(str(config, "path"));
                encoding.setText(str(config, "encoding", "UTF-8"));
                fileDescription.setText(str(config, "description"));
            }
        }
        syncTypeForms.run();
        syncDriverDefaults.run();

        GridPane grid = EditorForms.dialogGrid();
        EditorForms.addRow(grid, 0, "资源类型", type);
        EditorForms.addRow(grid, 1, "名称", name);
        EditorForms.addRow(grid, 2, "配置", typeForms);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setMinWidth(720);
        dialog.getDialogPane().setPrefSize(760, 620);

        ButtonType testType = new ButtonType("测试连接", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().setAll(testType, ButtonType.OK, ButtonType.CANCEL);
        Button testBtn = (Button) dialog.getDialogPane().lookupButton(testType);
        testBtn.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (type.getValue() != ProjectResourceType.DATASOURCE) {
                showError("仅 JDBC 数据源支持连接测试");
                return;
            }
            DatasourceFormValue formValue = buildDatasourceFormValue(driver, host, port, database, urlParams,
                    username, password, dangerous);
            if (formValue.error() != null) {
                showError(formValue.error());
                return;
            }
            try {
                Long existingId = creating ? null : current.id();
                projectResources.testDatasourceConnection(formValue.config(), username.getText().trim(),
                        password.getText(), existingId);
                showInfo("连接测试", "连接成功");
            } catch (Exception ex) {
                showError(ex.getMessage() == null ? "连接失败" : ex.getMessage());
            }
        });
        Runnable syncTestButton = () -> {
            boolean datasource = type.getValue() == ProjectResourceType.DATASOURCE;
            testBtn.setDisable(!datasource);
            testBtn.setVisible(datasource);
            testBtn.setManaged(datasource);
        };
        type.valueProperty().addListener((obs, old, value) -> syncTestButton.run());
        syncTestButton.run();

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            if (name.getText().isBlank()) {
                showError("资源名称不能为空");
                return null;
            }
            Map<String, Object> config = new LinkedHashMap<>();
            String secret = null;
            if (type.getValue() == ProjectResourceType.DATASOURCE) {
                DatasourceFormValue formValue = buildDatasourceFormValue(driver, host, port, database, urlParams,
                        username, password, dangerous);
                if (formValue.error() != null) {
                    showError(formValue.error());
                    return null;
                }
                config.putAll(formValue.config());
                secret = formValue.secret();
            } else {
                if (filePath.getText().isBlank()) {
                    showError("文件路径不能为空");
                    return null;
                }
                config.put("path", filePath.getText().trim());
                config.put("encoding", encoding.getText().isBlank() ? "UTF-8" : encoding.getText().trim());
                config.put("description", fileDescription.getText().trim());
            }
            ProjectResource resource = new ProjectResource(
                    creating ? null : current.id(), projectId, type.getValue(),
                    name.getText().trim(), config, true);
            return new ProjectResourceInput(resource, secret);
        });
        return dialog.showAndWait();
    }

    private record DatasourceFormValue(Map<String, Object> config, String secret, String error) {}

    private static DatasourceFormValue buildDatasourceFormValue(ComboBox<DriverOption> driver, TextField host,
                                                                TextField port, TextField database,
                                                                TextField urlParams, TextField username,
                                                                PasswordField password, CheckBox dangerous) {
        DriverOption selectedDriver = driver.getValue();
        if (selectedDriver == null) {
            return new DatasourceFormValue(null, null, "请选择 JDBC 驱动");
        }
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("driverClass", selectedDriver.driverClass());
        config.put("driverLabel", selectedDriver.label());
        config.put("host", host.getText().trim());
        config.put("port", port.getText().trim());
        config.put("database", database.getText().trim());
        config.put("urlParams", urlParams.getText().trim());
        config.put("username", username.getText().trim());
        config.put("allowDangerousSql", dangerous.isSelected());
        if ("sqlite".equals(selectedDriver.vendor())) {
            if (database.getText().isBlank()) {
                return new DatasourceFormValue(null, null, "SQLite 数据库文件路径不能为空");
            }
        } else if (host.getText().isBlank() || database.getText().isBlank()) {
            return new DatasourceFormValue(null, null, "主机和数据库不能为空");
        }
        config.put("jdbcUrl", JdbcConnectionConfig.buildJdbcUrl(config));
        if (string(config, "jdbcUrl").isBlank()) {
            return new DatasourceFormValue(null, null, "无法生成 JDBC URL，请检查连接信息");
        }
        return new DatasourceFormValue(config, password.getText(), null);
    }

    record ProjectResourceInput(ProjectResource resource, String secret) {}

    private static String str(Map<String, Object> config, String key) {
        return str(config, key, "");
    }

    private static String str(Map<String, Object> config, String key, String fallback) {
        if (config == null) return fallback;
        Object value = config.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean bool(Map<String, Object> config, String key) {
        if (config == null) return false;
        Object value = config.get(key);
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }

    private static String string(Map<String, Object> config, String key) {
        return str(config, key, "");
    }

    static void manageVariables(ScopeType type, Long scopeId, ScopedVariableService scopedVariables, ObjectMapper mapper) {
        Dialog<Void> dialog = base("环境变量管理");
        ListView<ScopedVariable> list = new ListView<>();
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(ScopedVariable item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.key() + " = " + json(mapper, item.value()));
            }
        });
        Runnable reload = () -> list.setItems(FXCollections.observableArrayList(scopedVariables.list(type, scopeId)));
        Button add = new Button("新增");
        Button edit = new Button("编辑");
        Button delete = new Button("删除");
        add.setOnAction(e -> variableDialog(type, scopeId, null, mapper).ifPresent(v -> { scopedVariables.save(v); reload.run(); }));
        edit.setOnAction(e -> {
            ScopedVariable selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) variableDialog(type, scopeId, selected, mapper).ifPresent(v -> { scopedVariables.save(v); reload.run(); });
        });
        delete.setOnAction(e -> {
            ScopedVariable selected = list.getSelectionModel().getSelectedItem();
            if (selected != null && confirm("确认删除变量“" + selected.key() + "”？")) { scopedVariables.delete(selected.id()); reload.run(); }
        });
        VBox box = new VBox(10, list, new ToolBar(add, edit, delete));
        box.setPadding(new Insets(10)); list.setPrefSize(560, 360);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().remove(ButtonType.OK);
        reload.run(); dialog.showAndWait();
    }

    static void executionDetails(ExecutionSummary summary,
                                 List<StepExecutionDetail> steps, ObjectMapper mapper) {
        Dialog<Void> dialog = base("执行详情 - " + summary.targetName());
        TableView<StepExecutionDetail> table = new TableView<>();
        table.getColumns().addAll(detailColumn("阶段", StepExecutionDetail::phase),
                detailColumn("步骤", StepExecutionDetail::stepCode),
                detailColumn("状态", StepExecutionDetail::status),
                detailColumn("耗时(ms)", s -> String.valueOf(s.elapsedMs())),
                detailColumn("错误", s -> s.errorMessage() == null ? "" : s.errorMessage()));
        table.setItems(FXCollections.observableArrayList(steps));
        TextArea content = new TextArea("双击步骤查看请求、响应、输出、提取和断言");
        content.setEditable(false); content.setPrefRowCount(18);
        table.setRowFactory(view -> {
            TableRow<StepExecutionDetail> row = new TableRow<>();
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

    private static TableColumn<StepExecutionDetail, String> detailColumn(
            String title, java.util.function.Function<StepExecutionDetail, String> value) {
        TableColumn<StepExecutionDetail, String> column = new TableColumn<>(title);
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

    static void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, """
                WorkflowTest 本地工作台

                纯客户端模式：JavaFX + SQLite
                全局变量配置：application.yml → workflowtest.global.*
                引用方式：${global.变量名}
                """, ButtonType.OK);
        alert.setTitle("关于");
        alert.setHeaderText("WorkflowTest");
        alert.showAndWait();
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
    private static TextArea area(String text, int rows) {
        TextArea area = new TextArea(text == null ? "" : text);
        area.setPrefRowCount(rows);
        area.setWrapText(false);
        area.setPrefWidth(360);
        GridPane.setHgrow(area, Priority.ALWAYS);
        return area;
    }
    private static String defaultConfig(StepType type) {
        if (type == null) return "{}";
        return switch (type) {
            case HTTP -> "{\n  \"method\": \"GET\",\n  \"url\": \"${workflow.baseUrl}/health\",\n  \"headers\": {},\n  \"readTimeoutMs\": 10000\n}";
            case SQL -> "{\n  \"datasourceId\": \"\",\n  \"operation\": \"QUERY\",\n  \"sql\": \"SELECT 1 AS value\",\n  \"parameters\": {}\n}";
            case DELAY -> "{\n  \"millis\": 1000\n}";
        };
    }
    private static String json(ObjectMapper mapper, Object value) {
        try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value); }
        catch (Exception e) { return "null"; }
    }
}
