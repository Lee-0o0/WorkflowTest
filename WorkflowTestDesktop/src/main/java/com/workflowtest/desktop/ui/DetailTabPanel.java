package com.workflowtest.desktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.support.EnvironmentPreviewService;
import com.workflowtest.engine.api.definition.ProjectResourceService;
import com.workflowtest.engine.api.definition.ProjectService;
import com.workflowtest.engine.api.definition.ScopedVariableService;
import com.workflowtest.engine.api.definition.StepDefinitionService;
import com.workflowtest.engine.api.definition.WorkflowDefinitionService;
import com.workflowtest.engine.api.definition.WorkflowGroupService;
import com.workflowtest.engine.support.JdbcConnectionConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DetailTabPanel {
    private final ProjectService projectService;
    private final WorkflowGroupService workflowGroups;
    private final WorkflowDefinitionService workflowDefinitions;
    private final StepDefinitionService stepDefinitions;
    private final ScopedVariableService scopedVariables;
    private final EnvironmentPreviewService environmentPreview;
    private final ProjectResourceService projectResources;
    private final ObjectMapper mapper;
    private final VBox body = new VBox(16);
    private final ScrollPane scroll = new ScrollPane(body);

    private EditorContext context = EditorContext.empty();

    @PostConstruct
    void init() {
        body.setPadding(new Insets(18));
        body.getStyleClass().add("details-panel");
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        showEmpty();
    }

    public Node root() {
        return scroll;
    }

    public void showEmpty() {
        context = EditorContext.empty();
        body.getChildren().setAll(sectionTitle("编辑"), hint("在左侧选择节点，或在空白处/节点上右键创建内容；双击项目可打开项目详情。"));
    }

    public void showSelection(Selection selection, EditorActions actions) {
        if (selection == null || selection.kind() == SelectionKind.ROOT) {
            showEmpty();
            return;
        }
        if (selection.kind() == SelectionKind.PROJECT) {
            showProjectDetail(selection, actions);
            return;
        }
        context = EditorContext.from(selection);
        body.getChildren().clear();
        body.getChildren().add(actionBar(selection, actions));
        switch (selection.kind()) {
            case GROUP -> renderGroup((Group) selection.value(), false, actions);
            case WORKFLOW -> renderWorkflow((Workflow) selection.value(), false, actions);
            case STEP, HOOK_STEP -> renderStep((Step) selection.value(), selection.projectId(), actions);
            case HOOK -> renderHook((Hook) selection.value());
            default -> body.getChildren().add(hint("当前节点不支持编辑。"));
        }
        if (selection.projectId() != null && selection.kind() != SelectionKind.STEP && selection.kind() != SelectionKind.HOOK_STEP) {
            renderEnvironment(selection);
        }
        if (selection.kind() == SelectionKind.GROUP || selection.kind() == SelectionKind.WORKFLOW) {
            renderVariables(selection, actions);
        }
    }

    public void showProjectDetail(Selection selection, EditorActions actions) {
        if (selection == null || selection.kind() != SelectionKind.PROJECT) return;
        context = EditorContext.from(selection);
        body.getChildren().clear();
        Project project = (Project) selection.value();
        TabPane projectTabs = new TabPane();
        projectTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        projectTabs.getStyleClass().add("project-detail-tabs");
        projectTabs.getTabs().addAll(
                tab("项目介绍", buildProjectIntroPanel(project, actions)),
                tab("环境变量", buildProjectEnvPanel(selection.id(), actions)),
                tab("项目资源", buildProjectResourcesPanel(selection.id(), actions)));
        VBox.setVgrow(projectTabs, Priority.ALWAYS);
        projectTabs.setMinHeight(480);
        body.getChildren().add(projectTabs);
    }

    public void showCreate(CreateRequest request, EditorActions actions) {
        context = EditorContext.fromCreate(request);
        body.getChildren().clear();
        body.getChildren().add(actionBarForCreate(request, actions));
        switch (request.kind()) {
            case PROJECT -> renderProject(new Project(null, "新项目", "", true), true, actions);
            case GROUP -> renderGroup(new Group(null, request.projectId(), "新组", "", 0, true), true, actions);
            case WORKFLOW -> renderWorkflow(new Workflow(null, request.groupId(), "新工作流", "", 0, true), true, actions);
            case STEP -> renderNewStep(request.ownerId(), false, request.projectId(), actions);
            case HOOK_STEP -> renderNewStep(request.ownerId(), true, request.projectId(), actions);
            default -> showEmpty();
        }
    }

    private HBox actionBar(Selection selection, EditorActions actions) {
        Button save = UiIcons.textButton(Feather.SAVE, "保存", () -> save(selection, actions), "accent");
        Button delete = UiIcons.textButton(Feather.TRASH_2, "删除", () -> delete(selection, actions), "danger");
        Button up = UiIcons.iconButton(Feather.ARROW_UP, "上移", () -> move(selection, -1, actions));
        Button down = UiIcons.iconButton(Feather.ARROW_DOWN, "下移", () -> move(selection, 1, actions));
        up.setDisable(!canMove(selection));
        down.setDisable(!canMove(selection));
        delete.setDisable(selection.kind() == SelectionKind.HOOK);
        HBox bar = new HBox(8, save, delete, up, down);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox actionBarForCreate(CreateRequest request, EditorActions actions) {
        Button save = UiIcons.textButton(Feather.SAVE, "保存", () -> saveCreate(request, actions), "accent");
        Button cancel = UiIcons.textButton(Feather.X, "取消", this::showEmpty);
        HBox bar = new HBox(8, save, cancel);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void renderProject(Project project, boolean creating, EditorActions actions) {
        TextField name = new TextField(project.name());
        TextArea description = new TextArea(nullSafe(project.description()));
        description.setPrefRowCount(3);
        GridPane grid = EditorForms.grid();
        EditorForms.addRow(grid, 0, "名称", name);
        EditorForms.addRow(grid, 1, "说明", description);
        body.getChildren().addAll(sectionTitle(creating ? "新建项目" : "编辑项目"), grid);
        context = context.withHandlers(() -> {
            if (name.getText().isBlank()) throw new IllegalArgumentException("名称不能为空");
            if (creating) projectService.save(null, name.getText().trim(), description.getText().trim());
            else projectService.save(project.id(), name.getText().trim(), description.getText().trim());
        });
    }

    private Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private VBox buildProjectIntroPanel(Project project, EditorActions actions) {
        TextField name = new TextField(project.name());
        TextArea description = new TextArea(nullSafe(project.description()));
        description.setPrefRowCount(6);
        GridPane grid = EditorForms.grid();
        EditorForms.addRow(grid, 0, "名称", name);
        EditorForms.addRow(grid, 1, "说明", description);
        Button save = UiIcons.textButton(Feather.SAVE, "保存项目", () -> {
            try {
                if (name.getText().isBlank()) throw new IllegalArgumentException("名称不能为空");
                projectService.save(project.id(), name.getText().trim(), description.getText().trim());
                actions.refreshTree();
                actions.onStatus("项目已保存");
            } catch (Exception e) {
                actions.fail(e);
            }
        }, "accent");
        VBox panel = new VBox(16, sectionTitle("项目介绍"), grid, save);
        panel.setPadding(new Insets(8, 4, 8, 4));
        return panel;
    }

    private VBox buildProjectEnvPanel(Long projectId, EditorActions actions) {
        VBox panel = new VBox(12, variablePrefixHint(),
                renderScopeVariableTable(ScopeType.PROJECT, projectId, "项目环境变量", actions));
        panel.setPadding(new Insets(8, 4, 8, 4));
        VBox.setVgrow(panel.getChildren().get(1), Priority.ALWAYS);
        return panel;
    }

    private VBox buildProjectResourcesPanel(Long projectId, EditorActions actions) {
        TabPane categoryTabs = new TabPane();
        categoryTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        categoryTabs.getTabs().addAll(
                tab("全部", buildProjectResourceTable(projectId, null, actions)),
                tab("数据源", buildProjectResourceTable(projectId, ProjectResourceType.DATASOURCE, actions)),
                tab("文件", buildProjectResourceTable(projectId, ProjectResourceType.FILE, actions)));
        categoryTabs.setMinHeight(420);
        VBox panel = new VBox(8, hint("按类型管理项目资源；SQL 步骤通过 datasourceId 引用数据源资源。"), categoryTabs);
        panel.setPadding(new Insets(8, 4, 8, 4));
        VBox.setVgrow(categoryTabs, Priority.ALWAYS);
        return panel;
    }

    private VBox buildProjectResourceTable(Long projectId, ProjectResourceType type, EditorActions actions) {
        TableView<ProjectResource> table = new TableView<>();
        TableColumn<ProjectResource, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().name()));
        TableColumn<ProjectResource, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(resourceTypeLabel(cell.getValue().type())));
        TableColumn<ProjectResource, String> summaryCol = new TableColumn<>("配置摘要");
        summaryCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(resourceSummary(cell.getValue())));
        table.getColumns().addAll(nameCol, typeCol, summaryCol);

        Runnable reload = () -> table.setItems(FXCollections.observableArrayList(
                type == null ? projectResources.list(projectId)
                        : projectResources.list(projectId, type)));

        java.util.function.Consumer<ProjectResource> editResource = selected ->
                EditorDialogs.projectResourceDialog(projectId, selected, projectResources).ifPresent(input -> {
                    projectResources.save(input.resource(), input.secret());
                    reload.run();
                    actions.onStatus("项目资源已保存");
                });
        Runnable addResource = () -> EditorDialogs.projectResourceDialog(projectId, null, projectResources).ifPresent(input -> {
            projectResources.save(input.resource(), input.secret());
            reload.run();
            actions.onStatus("项目资源已保存");
        });
        table.getColumns().add(UiIcons.actionsColumn(editResource, selected -> {
            if (actions.confirm("确认删除资源「" + selected.name() + "」？")) {
                projectResources.delete(selected.id());
                reload.run();
                actions.onStatus("项目资源已删除");
            }
        }));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(320);
        table.setRowFactory(view -> {
            TableRow<ProjectResource> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) editResource.accept(row.getItem());
            });
            return row;
        });

        Button add = UiIcons.textButton(Feather.PLUS, "新增", addResource);

        reload.run();
        return new VBox(10, new HBox(8, add), table);
    }

    private String resourceTypeLabel(ProjectResourceType type) {
        return switch (type) {
            case DATASOURCE -> "数据源";
            case FILE -> "文件";
        };
    }

    private String resourceSummary(ProjectResource resource) {
        Map<String, Object> config = resource.config();
        if (config == null) return "";
        return switch (resource.type()) {
            case DATASOURCE -> {
                Map<String, Object> enriched = new java.util.LinkedHashMap<>(config);
                JdbcConnectionConfig.enrichFromJdbcUrl(enriched);
                String host = String.valueOf(enriched.getOrDefault("host", ""));
                String port = String.valueOf(enriched.getOrDefault("port", ""));
                String database = String.valueOf(enriched.getOrDefault("database", ""));
                if (!host.isBlank()) yield host + (port.isBlank() ? "" : ":" + port) + "/" + database;
                yield String.valueOf(enriched.getOrDefault("jdbcUrl", ""));
            }
            case FILE -> String.valueOf(config.getOrDefault("path", ""));
        };
    }

    private void renderGroup(Group group, boolean creating, EditorActions actions) {
        TextField name = new TextField(group.name());
        TextArea description = new TextArea(nullSafe(group.description()));
        description.setPrefRowCount(3);
        GridPane grid = EditorForms.grid();
        EditorForms.addRow(grid, 0, "名称", name);
        EditorForms.addRow(grid, 1, "说明", description);
        body.getChildren().addAll(sectionTitle(creating ? "新建组" : "编辑组"), grid);
        context = context.withHandlers(() -> {
            if (name.getText().isBlank()) throw new IllegalArgumentException("名称不能为空");
            if (creating) workflowGroups.save(null, group.projectId(), name.getText().trim(), description.getText().trim(), 0);
            else workflowGroups.save(group.id(), group.projectId(), name.getText().trim(), description.getText().trim(), group.sortOrder());
        });
    }

    private void renderWorkflow(Workflow workflow, boolean creating, EditorActions actions) {
        TextField name = new TextField(workflow.name());
        TextArea description = new TextArea(nullSafe(workflow.description()));
        description.setPrefRowCount(3);
        GridPane grid = EditorForms.grid();
        EditorForms.addRow(grid, 0, "名称", name);
        EditorForms.addRow(grid, 1, "说明", description);
        body.getChildren().addAll(sectionTitle(creating ? "新建工作流" : "编辑工作流"), grid);
        context = context.withHandlers(() -> {
            if (name.getText().isBlank()) throw new IllegalArgumentException("名称不能为空");
            if (creating) workflowDefinitions.save(null, workflow.groupId(), name.getText().trim(), description.getText().trim(), 0);
            else workflowDefinitions.save(workflow.id(), workflow.groupId(), name.getText().trim(), description.getText().trim(), workflow.sortOrder());
        });
    }

    private void renderStep(Step step, Long projectId, EditorActions actions) {
        body.getChildren().add(sectionTitle("编辑步骤"));
        body.getChildren().add(buildStepForm(step.id(), step.ownerId(), step, step.hookStep(), projectId));
    }

    private void renderNewStep(Long ownerId, boolean hookStep, Long projectId, EditorActions actions) {
        body.getChildren().add(sectionTitle(hookStep ? "新建前置钩子步骤" : "新建步骤"));
        body.getChildren().add(buildStepForm(null, ownerId, null, hookStep, projectId));
    }

    private GridPane buildStepForm(Long stepId, Long ownerId, Step current, boolean hookStep, Long projectId) {
        TextField code = new TextField(current == null ? "step" : current.code());
        TextField name = new TextField(current == null ? "新步骤" : current.name());
        ComboBox<StepType> type = new ComboBox<>();
        type.getItems().setAll(StepType.values());
        type.setValue(current == null ? StepType.HTTP : current.type());
        Spinner<Integer> order = new Spinner<>(0, 9999, current == null ? 0 : current.sortOrder());
        TextArea config = EditorForms.jsonArea(current == null ? EditorForms.defaultConfig(type.getValue()) : current.configJson(), 8);
        TextArea extraction = EditorForms.jsonArea(current == null ? "[]" : current.extractionJson(), 4);
        TextArea assertion = EditorForms.jsonArea(current == null ? "[]" : current.assertionJson(), 4);

        ComboBox<ProjectResource> datasource = new ComboBox<>();
        java.util.List<ProjectResource> datasourceOptions = projectId == null ? java.util.List.of()
                : projectResources.list(projectId, ProjectResourceType.DATASOURCE);
        datasource.getItems().setAll(datasourceOptions);
        datasource.setMaxWidth(Double.MAX_VALUE);
        datasource.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(ProjectResource value) {
                return value == null ? "" : value.name();
            }
            @Override public ProjectResource fromString(String string) { return null; }
        });
        ComboBox<String> operation = new ComboBox<>();
        operation.getItems().setAll("QUERY", "UPDATE");
        operation.setValue("QUERY");
        TextArea sql = EditorForms.jsonArea("SELECT 1 AS value", 6);
        TextArea parameters = EditorForms.jsonArea("{}", 3);
        Label datasourceHint = hint(datasourceOptions.isEmpty()
                ? "当前项目尚未配置数据源，请先在「项目资源 → 数据源」中新增。"
                : "选择本项目已配置的数据源；SQL 步骤通过 datasourceId 引用。");
        GridPane sqlGrid = EditorForms.grid();
        EditorForms.addRow(sqlGrid, 0, "数据源", datasource);
        EditorForms.addRow(sqlGrid, 1, "操作", operation);
        EditorForms.addRow(sqlGrid, 2, "SQL", sql);
        EditorForms.addRow(sqlGrid, 3, "参数 JSON", parameters);
        VBox sqlForm = new VBox(8, datasourceHint, sqlGrid);

        StackPane configPanel = new StackPane(config, sqlForm);
        Runnable syncTypeForm = () -> {
            boolean sqlStep = type.getValue() == StepType.SQL;
            config.setVisible(!sqlStep);
            config.setManaged(!sqlStep);
            sqlForm.setVisible(sqlStep);
            sqlForm.setManaged(sqlStep);
        };
        Runnable resetSqlDefaults = () -> {
            operation.setValue("QUERY");
            sql.setText("SELECT 1 AS value");
            parameters.setText("{}");
            if (!datasourceOptions.isEmpty()) datasource.setValue(datasourceOptions.getFirst());
            else datasource.setValue(null);
        };
        if (current != null && current.type() == StepType.SQL) {
            EditorForms.applySqlConfig(mapper, current.configJson(), datasource, operation, sql, parameters);
        } else if (current == null && type.getValue() == StepType.SQL) {
            resetSqlDefaults.run();
        }
        type.valueProperty().addListener((obs, old, value) -> {
            if (current == null && config.getText().equals(EditorForms.defaultConfig(old))) {
                config.setText(EditorForms.defaultConfig(value));
            }
            if (value == StepType.SQL && current == null && old != StepType.SQL) {
                resetSqlDefaults.run();
            }
            syncTypeForm.run();
        });
        syncTypeForm.run();

        GridPane grid = EditorForms.grid();
        EditorForms.addRow(grid, 0, "编码", code);
        EditorForms.addRow(grid, 1, "名称", name);
        EditorForms.addRow(grid, 2, "类型", type);
        EditorForms.addRow(grid, 3, "顺序", order);
        EditorForms.addRow(grid, 4, "步骤配置", configPanel);
        EditorForms.addRow(grid, 5, "提取 JSON", extraction);
        EditorForms.addRow(grid, 6, "断言 JSON", assertion);
        context = context.withHandlers(() -> {
            String configJson = type.getValue() == StepType.SQL
                    ? EditorForms.buildSqlConfigJson(mapper, datasource.getValue(), operation.getValue(),
                    sql.getText(), parameters.getText())
                    : config.getText();
            Step step = new Step(stepId, ownerId, code.getText().trim(), name.getText().trim(), type.getValue(),
                    order.getValue(), true, configJson, extraction.getText(), assertion.getText(), hookStep);
            if (hookStep) stepDefinitions.saveHookStep(ownerId, step);
            else stepDefinitions.saveWorkflowStep(step);
        });
        return grid;
    }

    private void renderHook(Hook hook) {
        String title = hook.hookType() == HookType.BEFORE_GROUP ? "组前置钩子" : "组后置钩子";
        body.getChildren().addAll(sectionTitle(title),
                new Label("类型：" + hook.hookType()),
                hint("钩子下的步骤请在左侧树中选择具体步骤进行编辑。"));
    }

    private void renderEnvironment(Selection selection) {
        EffectiveEnvironment env = environmentPreview.preview(selection.projectId(), selection.groupId(), selection.workflowId());
        TableView<EnvRow> table = new TableView<>();
        TableColumn<EnvRow, String> key = envColumn("变量", EnvRow::key);
        TableColumn<EnvRow, String> value = envColumn("最终值", EnvRow::value);
        TableColumn<EnvRow, String> source = envColumn("来源", EnvRow::source);
        table.getColumns().addAll(key, value, source);
        env.effective().forEach((k, v) -> table.getItems().add(new EnvRow(k,
                String.valueOf(v), env.sources().get(k))));
        table.setPrefHeight(Math.min(260, 40 + table.getItems().size() * 28));
        body.getChildren().addAll(sectionTitle("有效环境变量（只读）"), table);
    }

    private void renderVariables(Selection selection, EditorActions actions) {
        ScopeType scopeType = switch (selection.kind()) {
            case GROUP -> ScopeType.GROUP;
            case WORKFLOW -> ScopeType.WORKFLOW;
            default -> throw new IllegalStateException();
        };
        String title = scopeType == ScopeType.GROUP ? "组环境变量" : "工作流环境变量";
        body.getChildren().add(variablePrefixHint());
        body.getChildren().add(renderScopeVariableTable(scopeType, selection.id(), title, actions));
    }

    private VBox renderScopeVariableTable(ScopeType scopeType, Long scopeId, String title, EditorActions actions) {
        TableView<ScopedVariable> table = new TableView<>();
        TableColumn<ScopedVariable, String> keyCol = new TableColumn<>("变量名");
        keyCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().key()));
        TableColumn<ScopedVariable, String> valueCol = new TableColumn<>("值");
        valueCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                EditorForms.json(mapper, cell.getValue().value())));
        table.getColumns().addAll(keyCol, valueCol);

        Runnable reload = () -> table.setItems(FXCollections.observableArrayList(scopedVariables.list(scopeType, scopeId)));

        java.util.function.Consumer<ScopedVariable> editVariable = selected ->
                EditorDialogs.variableDialog(scopeType, scopeId, selected, mapper).ifPresent(v -> {
                    scopedVariables.save(v);
                    reload.run();
                    actions.onStatus("环境变量已保存");
                });
        Runnable addVariable = () -> EditorDialogs.variableDialog(scopeType, scopeId, null, mapper).ifPresent(v -> {
            scopedVariables.save(v);
            reload.run();
            actions.onStatus("环境变量已保存");
        });
        table.getColumns().add(UiIcons.actionsColumn(editVariable, selected -> {
            if (actions.confirm("确认删除变量「" + selected.key() + "」？")) {
                scopedVariables.delete(selected.id());
                reload.run();
                actions.onStatus("环境变量已删除");
            }
        }));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(280);
        table.setRowFactory(view -> {
            TableRow<ScopedVariable> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) editVariable.accept(row.getItem());
            });
            return row;
        });

        Button add = UiIcons.textButton(Feather.PLUS, "新增", addVariable);

        reload.run();
        VBox panel = new VBox(10, sectionTitle(title), new HBox(8, add), table);
        return panel;
    }

    private Label variablePrefixHint() {
        return hint("""
                变量引用：全局 ${global.xxx}，项目 ${project.xxx}，组 ${group.xxx}，\
                工作流 ${workflow.xxx} 或 ${xxx}；组级提取 ${group.xxx}，工作流级提取 ${workflow.xxx}，步骤结果 ${steps.xxx}。""");
    }

    private void save(Selection selection, EditorActions actions) {
        try {
            context.save.run();
            actions.refreshTree();
            actions.onStatus("保存成功");
        } catch (Exception e) {
            actions.fail(e);
        }
    }

    private void saveCreate(CreateRequest request, EditorActions actions) {
        try {
            context.save.run();
            actions.refreshTree();
            actions.selectEditTab();
            actions.onStatus("创建成功");
        } catch (Exception e) {
            actions.fail(e);
        }
    }

    private void delete(Selection selection, EditorActions actions) {
        if (!actions.confirm("确认删除「" + selection.name() + "」？")) return;
        try {
            switch (selection.kind()) {
                case PROJECT -> projectService.delete(selection.id());
                case GROUP -> workflowGroups.delete(selection.id());
                case WORKFLOW -> workflowDefinitions.delete(selection.id());
                case STEP -> stepDefinitions.delete(selection.id(), false);
                case HOOK_STEP -> stepDefinitions.delete(selection.id(), true);
                default -> throw new IllegalArgumentException("当前节点不可删除");
            }
            showEmpty();
            actions.refreshTree();
            actions.onStatus("已删除");
        } catch (Exception e) {
            actions.fail(e);
        }
    }

    private void move(Selection selection, int delta, EditorActions actions) {
        try {
            switch (selection.kind()) {
                case GROUP -> workflowGroups.move(selection.id(), delta);
                case WORKFLOW -> workflowDefinitions.move(selection.id(), delta);
                case STEP, HOOK_STEP -> {
                    Step step = (Step) selection.value();
                    stepDefinitions.move(selection.id(), step.hookStep(), delta);
                }
                default -> throw new IllegalArgumentException("当前节点不可排序");
            }
            actions.refreshTree();
        } catch (Exception e) {
            actions.fail(e);
        }
    }

    private boolean canMove(Selection selection) {
        return selection.kind() == SelectionKind.GROUP || selection.kind() == SelectionKind.WORKFLOW
                || selection.kind() == SelectionKind.STEP || selection.kind() == SelectionKind.HOOK_STEP;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint-label");
        label.setWrapText(true);
        return label;
    }

    private TableColumn<EnvRow, String> envColumn(String title, java.util.function.Function<EnvRow, String> value) {
        TableColumn<EnvRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public enum SelectionKind { ROOT, PROJECT, GROUP, WORKFLOW, HOOK, STEP, HOOK_STEP }

    public record Selection(SelectionKind kind, Long id, String name, Long projectId,
                            Long groupId, Long workflowId, Object value) {}

    public record CreateRequest(SelectionKind kind, Long projectId, Long groupId, Long workflowId, Long ownerId) {}

    public interface EditorActions {
        void refreshTree();
        void selectEditTab();
        void onStatus(String message);
        void fail(Throwable error);
        void showInfo(String title, String message);
        boolean confirm(String message);
    }

    private record EnvRow(String key, String value, String source) {}

    private static final class EditorContext {
        private final Runnable save;
        private final Long variableId;
        private final ScopeType variableScopeType;
        private final Long variableScopeId;
        private final TextField variableKey;
        private final TextArea variableValue;

        private EditorContext(Runnable save, Long variableId, ScopeType variableScopeType, Long variableScopeId,
                              TextField variableKey, TextArea variableValue) {
            this.save = save;
            this.variableId = variableId;
            this.variableScopeType = variableScopeType;
            this.variableScopeId = variableScopeId;
            this.variableKey = variableKey;
            this.variableValue = variableValue;
        }

        static EditorContext empty() {
            return new EditorContext(() -> {}, null, null, null, null, null);
        }

        static EditorContext from(Selection selection) {
            return empty();
        }

        static EditorContext fromCreate(CreateRequest request) {
            return empty();
        }

        EditorContext withHandlers(Runnable saveAction) {
            return new EditorContext(saveAction, variableId, variableScopeType, variableScopeId,
                    variableKey, variableValue);
        }

        EditorContext withVariableEdit(Long id, ScopeType scopeType, Long scopeId,
                                       TextField key, TextArea value) {
            return new EditorContext(save, id, scopeType, scopeId, key, value);
        }
    }
}
