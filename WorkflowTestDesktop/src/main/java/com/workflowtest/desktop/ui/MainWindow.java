package com.workflowtest.desktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.BackupService;
import com.workflowtest.engine.api.DefinitionModels.*;
import com.workflowtest.engine.api.DefinitionService;
import com.workflowtest.engine.api.ExecutionModels.*;
import com.workflowtest.engine.api.ExecutionQueryService;
import com.workflowtest.engine.api.WorkflowExecutionService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class MainWindow {
    private final DefinitionService definitions;
    private final WorkflowExecutionService executions;
    private final ExecutionQueryService history;
    private final ObjectMapper objectMapper;
    private final BackupService backups;
    private final RemoteAssetWindow remoteAssets;

    private final BorderPane root = new BorderPane();
    private final TreeView<NodeRef> tree = new TreeView<>();
    private final VBox details = new VBox(10);
    private final TextArea executionLog = new TextArea();
    private final Label status = new Label("本地模式（SQLite）· 就绪");
    private final TableView<ExecutionQueryService.ExecutionSummary> historyTable = new TableView<>();
    private ExecutionHandle activeExecution;

    public MainWindow(DefinitionService definitions, WorkflowExecutionService executions,
                      ExecutionQueryService history, ObjectMapper objectMapper, BackupService backups,
                      RemoteAssetWindow remoteAssets) {
        this.definitions = definitions; this.executions = executions;
        this.history = history; this.objectMapper = objectMapper; this.backups = backups; this.remoteAssets = remoteAssets;
        build();
        refreshTree();
    }

    public Parent root() { return root; }

    private void build() {
        root.setTop(toolbar());
        tree.setShowRoot(true);
        tree.setPrefWidth(340);
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> showDetails(value));
        tree.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) editSelected();
        });

        details.setPadding(new Insets(18));
        ScrollPane detailScroll = new ScrollPane(details);
        detailScroll.setFitToWidth(true);

        executionLog.setEditable(false);
        executionLog.setWrapText(true);
        executionLog.setStyle("-fx-font-family: Consolas;");

        configureHistoryTable();
        TabPane tabs = new TabPane(
                tab("详情", detailScroll), tab("执行日志", executionLog), tab("历史记录", historyTable));
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, old, value) -> {
            if (value != null && "历史记录".equals(value.getText())) refreshHistory();
        });
        SplitPane split = new SplitPane(tree, tabs);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.25);
        root.setCenter(split);
        HBox statusBar = new HBox(status); statusBar.getStyleClass().add("status-bar");
        root.setBottom(statusBar);
    }

    private ToolBar toolbar() {
        Button remote = button("集中资产（可选）", () -> remoteAssets.show(root.getScene() == null ? null : root.getScene().getWindow()), false);
        Button addProject = button("新建项目", this::addProject, false);
        Button addGroup = button("新建组", this::addGroup, false);
        Button addWorkflow = button("新建工作流", this::addWorkflow, false);
        Button addStep = button("新增步骤", this::addStep, false);
        Button addHook = button("新增前置钩子步骤", this::addHookStep, false);
        Button variable = button("环境变量", this::editVariables, false);
        Button datasource = button("数据源", this::addDataSource, false);
        Button up = button("上移", () -> moveSelected(-1), false);
        Button down = button("下移", () -> moveSelected(1), false);
        Button backup = button("备份", this::backup, false);
        Button run = button("运行", this::runSelected, true);
        Button stop = button("停止", this::stopExecution, false);
        Button refresh = button("刷新", this::refreshTree, false);
        Button delete = button("删除", this::deleteSelected, false); delete.getStyleClass().add("danger");
        return new ToolBar(remote, new Separator(), addProject, addGroup, addWorkflow, addStep, addHook,
                new Separator(), variable, datasource, up, down, new Separator(), run, stop, backup,
                new Separator(), refresh, delete);
    }

    private void refreshTree() {
        try {
            TreeItem<NodeRef> rootItem = new TreeItem<>(new NodeRef(NodeType.ROOT, "root", "测试工程", null, null, null, null));
            rootItem.setExpanded(true);
            for (ProjectNode projectNode : definitions.loadTree().projects()) {
                Project project = projectNode.project();
                TreeItem<NodeRef> projectItem = new TreeItem<>(new NodeRef(NodeType.PROJECT, project.id(), project.name(),
                        project.id(), null, null, project));
                projectItem.setExpanded(true);
                for (GroupNode groupNode : projectNode.groups()) {
                    Group group = groupNode.group();
                    TreeItem<NodeRef> groupItem = new TreeItem<>(new NodeRef(NodeType.GROUP, group.id(), group.name(),
                            project.id(), group.id(), null, group));
                    groupItem.setExpanded(true);
                    addHookNodes(groupItem, OwnerType.GROUP, group.id(), project.id(), group.id(), null);
                    for (WorkflowNode workflowNode : groupNode.workflows()) {
                        Workflow workflow = workflowNode.workflow();
                        TreeItem<NodeRef> workflowItem = new TreeItem<>(new NodeRef(NodeType.WORKFLOW, workflow.id(), workflow.name(),
                                project.id(), group.id(), workflow.id(), workflow));
                        workflowItem.setExpanded(true);
                        addHookNodes(workflowItem, OwnerType.WORKFLOW, workflow.id(), project.id(), group.id(), workflow.id());
                        for (Step step : workflowNode.steps()) {
                            workflowItem.getChildren().add(new TreeItem<>(new NodeRef(NodeType.STEP, step.id(),
                                    step.sortOrder() + ". " + step.name() + " [" + step.type() + "]",
                                    project.id(), group.id(), workflow.id(), step)));
                        }
                        groupItem.getChildren().add(workflowItem);
                    }
                    projectItem.getChildren().add(groupItem);
                }
                rootItem.getChildren().add(projectItem);
            }
            tree.setRoot(rootItem);
            status.setText("工程树已刷新");
        } catch (Exception e) { fail(e); }
    }

    private void addHookNodes(TreeItem<NodeRef> parent, OwnerType ownerType, String ownerId,
                              String projectId, String groupId, String workflowId) {
        for (Hook hook : definitions.listHooks(ownerType, ownerId)) {
            TreeItem<NodeRef> hookItem = new TreeItem<>(new NodeRef(NodeType.HOOK, hook.id(),
                    hook.hookType() == HookType.BEFORE_GROUP ? "组前置钩子" : "工作流前置钩子",
                    projectId, groupId, workflowId, hook));
            for (Step step : hook.steps()) {
                hookItem.getChildren().add(new TreeItem<>(new NodeRef(NodeType.HOOK_STEP, step.id(),
                        step.sortOrder() + ". " + step.name() + " [" + step.type() + "]",
                        projectId, groupId, workflowId, step)));
            }
            parent.getChildren().add(hookItem);
        }
    }

    private void showDetails(TreeItem<NodeRef> item) {
        details.getChildren().clear();
        if (item == null) return;
        NodeRef ref = item.getValue();
        Label title = new Label(ref.name()); title.getStyleClass().add("section-title");
        details.getChildren().addAll(title, new Label("类型：" + ref.type()));
        if (ref.value() instanceof Project p) details.getChildren().add(new Label(nullSafe(p.description())));
        if (ref.value() instanceof Group g) details.getChildren().add(new Label(nullSafe(g.description())));
        if (ref.value() instanceof Workflow w) details.getChildren().add(new Label(nullSafe(w.description())));
        if (ref.value() instanceof Hook hook) {
            details.getChildren().add(new Label("失败策略：" + hook.failureStrategy()));
        }
        if (ref.value() instanceof Step step) {
            TextArea config = readonly("配置\n" + pretty(step.configJson()) + "\n\n提取\n" + pretty(step.extractionJson())
                    + "\n\n断言\n" + pretty(step.assertionJson()));
            details.getChildren().add(config);
        }
        if (ref.projectId() != null) {
            showEnvironment(ref);
        }
    }

    private void showEnvironment(NodeRef ref) {
        EffectiveEnvironment env = definitions.previewEnvironment(ref.projectId(), ref.groupId(), ref.workflowId());
        Label label = new Label("有效环境变量"); label.getStyleClass().add("section-title");
        TableView<EnvRow> table = new TableView<>();
        TableColumn<EnvRow, String> key = column("变量", row -> row.key);
        TableColumn<EnvRow, String> value = column("最终值", row -> row.value);
        TableColumn<EnvRow, String> source = column("来源", row -> row.source);
        table.getColumns().addAll(key, value, source);
        env.effective().forEach((k, v) -> table.getItems().add(new EnvRow(k,
                env.sensitiveKeys().contains(k) ? "******" : String.valueOf(v), env.sources().get(k))));
        table.setPrefHeight(Math.min(320, 32 + table.getItems().size() * 28));
        details.getChildren().addAll(label, table);
    }

    private void addProject() {
        EditorDialogs.nameDialog("新建项目", "新项目", "").ifPresent(value -> action(() ->
                definitions.saveProject(null, value[0], value[1])));
    }

    private void addGroup() {
        NodeRef ref = selected();
        String projectId = ref == null ? null : ref.projectId();
        if (projectId == null) { EditorDialogs.showError("请先选择项目"); return; }
        EditorDialogs.nameDialog("新建组", "新组", "").ifPresent(value -> action(() ->
                definitions.saveGroup(null, projectId, value[0], value[1], 0)));
    }

    private void addWorkflow() {
        NodeRef ref = selected();
        String groupId = ref == null ? null : ref.groupId();
        if (groupId == null) { EditorDialogs.showError("请先选择组"); return; }
        EditorDialogs.nameDialog("新建工作流", "新工作流", "").ifPresent(value -> action(() ->
                definitions.saveWorkflow(null, groupId, value[0], value[1], 0)));
    }

    private void addStep() {
        NodeRef ref = selected();
        if (ref == null || ref.workflowId() == null) { EditorDialogs.showError("请先选择工作流"); return; }
        EditorDialogs.stepDialog(ref.workflowId(), null, false).ifPresent(step -> action(() -> definitions.saveWorkflowStep(step)));
    }

    private void addHookStep() {
        NodeRef ref = selected();
        if (ref == null || (ref.groupId() == null && ref.workflowId() == null)) {
            EditorDialogs.showError("请选择组或工作流"); return;
        }
        boolean workflow = ref.workflowId() != null;
        Hook hook = definitions.getOrCreateHook(workflow ? OwnerType.WORKFLOW : OwnerType.GROUP,
                workflow ? ref.workflowId() : ref.groupId(),
                workflow ? HookType.BEFORE_WORKFLOW : HookType.BEFORE_GROUP);
        EditorDialogs.stepDialog(hook.id(), null, true).ifPresent(step -> action(() -> definitions.saveHookStep(hook.id(), step)));
    }

    private void editVariables() {
        NodeRef ref = selected();
        if (ref == null || !(ref.type() == NodeType.PROJECT || ref.type() == NodeType.GROUP || ref.type() == NodeType.WORKFLOW)) {
            EditorDialogs.showError("请选择项目、组或工作流"); return;
        }
        ScopeType type = switch (ref.type()) {
            case PROJECT -> ScopeType.PROJECT;
            case GROUP -> ScopeType.GROUP;
            case WORKFLOW -> ScopeType.WORKFLOW;
            default -> throw new IllegalStateException();
        };
        String scopeId = ref.id();
        EditorDialogs.manageVariables(type, scopeId, definitions, objectMapper);
        refreshTree();
    }

    private void addDataSource() {
        NodeRef ref = selected();
        if (ref == null || ref.projectId() == null) { EditorDialogs.showError("请选择项目或其子节点"); return; }
        EditorDialogs.manageDataSources(ref.projectId(), definitions);
        refreshTree();
    }

    private void editSelected() {
        NodeRef ref = selected();
        if (ref == null) return;
        if (ref.value() instanceof Project p) EditorDialogs.nameDialog("编辑项目", p.name(), p.description())
                .ifPresent(v -> action(() -> definitions.saveProject(p.id(), v[0], v[1])));
        else if (ref.value() instanceof Group g) EditorDialogs.nameDialog("编辑组", g.name(), g.description())
                .ifPresent(v -> action(() -> definitions.saveGroup(g.id(), g.projectId(), v[0], v[1], g.sortOrder())));
        else if (ref.value() instanceof Workflow w) EditorDialogs.nameDialog("编辑工作流", w.name(), w.description())
                .ifPresent(v -> action(() -> definitions.saveWorkflow(w.id(), w.groupId(), v[0], v[1], w.sortOrder())));
        else if (ref.value() instanceof Step step) EditorDialogs.stepDialog(step.ownerId(), step, step.hookStep())
                .ifPresent(v -> action(() -> step.hookStep()
                        ? definitions.saveHookStep(step.ownerId(), v) : definitions.saveWorkflowStep(v)));
    }

    private void deleteSelected() {
        NodeRef ref = selected();
        if (ref == null || ref.type() == NodeType.ROOT || !EditorDialogs.confirm("确认删除“" + ref.name() + "”？")) return;
        action(() -> {
            switch (ref.type()) {
                case PROJECT -> definitions.deleteProject(ref.id());
                case GROUP -> definitions.deleteGroup(ref.id());
                case WORKFLOW -> definitions.deleteWorkflow(ref.id());
                case STEP -> definitions.deleteStep(ref.id(), false);
                case HOOK_STEP -> definitions.deleteStep(ref.id(), true);
                default -> { }
            }
            return null;
        });
    }

    private void runSelected() {
        NodeRef ref = selected();
        if (ref == null) return;
        executionLog.clear();
        if (ref.workflowId() != null) activeExecution = executions.submitWorkflow(
                new WorkflowExecutionCommand(ref.workflowId(), Map.of()), this::onEvent);
        else if (ref.groupId() != null) activeExecution = executions.submitGroup(
                new GroupExecutionCommand(ref.groupId(), Map.of()), this::onEvent);
        else { EditorDialogs.showError("请选择组或工作流"); return; }
        status.setText("执行中：" + ref.name());
        activeExecution.future().whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) { append("执行异常：" + error.getMessage()); status.setText("执行异常"); }
            else { append("完成：" + result.status() + "，耗时 " + result.elapsedMs() + "ms"); status.setText("执行完成：" + result.status()); }
            refreshHistory(); activeExecution = null;
        }));
    }

    private void stopExecution() {
        if (activeExecution != null) { executions.cancel(activeExecution.executionId()); status.setText("正在停止..."); }
    }

    private void onEvent(ExecutionEvent event) {
        Platform.runLater(() -> append(event.time().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
                + "  " + event.type() + "  " + nullSafe(event.code()) + "  " + nullSafe(event.message())));
    }

    private void configureHistoryTable() {
        historyTable.getColumns().addAll(
                column("类型", ExecutionQueryService.ExecutionSummary::type),
                column("目标", ExecutionQueryService.ExecutionSummary::targetName),
                column("状态", ExecutionQueryService.ExecutionSummary::status),
                column("开始时间", e -> e.startedAt() == null ? "" : e.startedAt().toString()),
                column("耗时(ms)", e -> String.valueOf(e.elapsedMs())),
                column("错误", e -> nullSafe(e.errorMessage())));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setRowFactory(table -> {
            TableRow<ExecutionQueryService.ExecutionSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) showExecutionDetails(row.getItem());
            });
            return row;
        });
    }

    private void showExecutionDetails(ExecutionQueryService.ExecutionSummary summary) {
        EditorDialogs.executionDetails(summary, history.steps(summary.id()), objectMapper);
    }

    private void moveSelected(int delta) {
        NodeRef ref = selected();
        if (ref == null) return;
        action(() -> {
            if (ref.value() instanceof Group) definitions.moveGroup(ref.id(), delta);
            else if (ref.value() instanceof Workflow) definitions.moveWorkflow(ref.id(), delta);
            else if (ref.value() instanceof Step step) definitions.moveStep(ref.id(), step.hookStep(), delta);
            else throw new IllegalArgumentException("请选择组、工作流或步骤进行排序");
            return null;
        });
    }

    private void backup() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择备份目录");
        var directory = chooser.showDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (directory == null) return;
        try {
            var file = backups.createBackup(directory.toPath());
            status.setText("备份完成：" + file);
            EditorDialogs.showInfo("备份完成", file.toString());
        } catch (Exception e) { fail(e); }
    }

    private void refreshHistory() {
        try { historyTable.setItems(FXCollections.observableArrayList(history.recent(100))); }
        catch (Exception e) { fail(e); }
    }

    private <T> TableColumn<T, String> column(String name, java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private Tab tab(String name, javafx.scene.Node content) { Tab tab = new Tab(name, content); tab.setClosable(false); return tab; }
    private Button button(String text, Runnable action, boolean primary) {
        Button button = new Button(text); button.setOnAction(e -> action.run());
        if (primary) button.getStyleClass().add("primary"); return button;
    }
    private NodeRef selected() { TreeItem<NodeRef> item = tree.getSelectionModel().getSelectedItem(); return item == null ? null : item.getValue(); }
    private void action(Action action) { try { action.run(); refreshTree(); } catch (Exception e) { fail(e); } }
    private void fail(Throwable e) { status.setText("失败：" + e.getMessage()); EditorDialogs.showError(e.getMessage() == null ? e.toString() : e.getMessage()); }
    private void append(String line) { executionLog.appendText(line + System.lineSeparator()); }
    private TextArea readonly(String value) { TextArea a = new TextArea(value); a.setEditable(false); a.setPrefRowCount(20); return a; }
    private String pretty(String json) { try { return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectMapper.readTree(json)); } catch (Exception e) { return json; } }
    private String nullSafe(String value) { return value == null ? "" : value; }

    private enum NodeType { ROOT, PROJECT, GROUP, WORKFLOW, HOOK, STEP, HOOK_STEP }
    private record NodeRef(NodeType type, String id, String name, String projectId,
                           String groupId, String workflowId, Object value) {
        @Override public String toString() { return name; }
    }
    private record EnvRow(String key, String value, String source) {}
    @FunctionalInterface private interface Action { Object run() throws Exception; }
}
