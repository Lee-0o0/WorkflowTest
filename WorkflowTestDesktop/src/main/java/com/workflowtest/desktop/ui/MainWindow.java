package com.workflowtest.desktop.ui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.support.BackupService;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.execution.ExecutionControlService;
import com.workflowtest.engine.api.execution.ExecutionHistoryService;
import com.workflowtest.engine.api.execution.ExecutionModels.*;
import com.workflowtest.engine.api.definition.GlobalVariableService;
import com.workflowtest.engine.api.execution.GroupExecutionService;
import com.workflowtest.engine.api.definition.HookDefinitionService;
import com.workflowtest.engine.api.execution.ProjectExecutionService;
import com.workflowtest.engine.api.definition.ProjectService;
import com.workflowtest.engine.api.definition.ProjectTreeService;
import com.workflowtest.engine.api.definition.StepDefinitionService;
import com.workflowtest.engine.api.execution.StepExecutionQueryService;
import com.workflowtest.engine.api.definition.WorkflowDefinitionService;
import com.workflowtest.engine.api.definition.WorkflowGroupService;
import com.workflowtest.engine.api.execution.WorkflowRunService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.kordamp.ikonli.feather.Feather;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class MainWindow implements TreeContextMenus.Host {
    private final ProjectTreeService projectTree;
    private final ProjectService projectService;
    private final GlobalVariableService globalVariables;
    private final WorkflowGroupService workflowGroups;
    private final WorkflowDefinitionService workflowDefinitions;
    private final StepDefinitionService stepDefinitions;
    private final HookDefinitionService hookDefinitions;
    private final ProjectExecutionService projectExecutions;
    private final GroupExecutionService groupExecutions;
    private final WorkflowRunService workflowRuns;
    private final ExecutionControlService executionControl;
    private final ExecutionHistoryService executionHistory;
    private final StepExecutionQueryService stepExecutions;
    private final ObjectMapper objectMapper;
    private final BackupService backups;
    private final DetailTabPanel detailTab;

    private final BorderPane root = new BorderPane();
    private final TreeView<NodeRef> tree = new TreeView<>();
    private final Label status = new Label("本地模式（SQLite）· 就绪");
    private final TableView<ExecutionSummary> historyTable = new TableView<>();
    private final Button themeToggle = UiIcons.iconButton(Feather.MOON, "切换主题", () -> { });
    private ExecutionHandle activeExecution;
    private ContextMenu activeContextMenu;
    private StackPane centerStack;
    private SplitPane projectWorkspace;
    private VBox globalEnvironmentView;
    private VBox executionHistoryView;
    private TableView<GlobalVariable> globalEnvTable;

    private enum CenterView { PROJECT, GLOBAL_ENV, HISTORY }

    @PostConstruct
    void init() {
        build();
        refreshTree();
    }

    public Parent root() { return root; }

    private void build() {
        root.getStyleClass().add("content-panel");
        root.setTop(headerBar());
        themeToggle.setOnAction(event -> toggleTheme());
        themeToggle.setTooltip(new Tooltip("切换为深色主题"));
        themeToggle.setTooltip(new Tooltip("切换为深色主题"));
        tree.setShowRoot(false);
        tree.setPrefWidth(340);
        tree.getStyleClass().add("tree-panel");
        configureTreeContextMenu();
        tree.getSelectionModel().selectedItemProperty().addListener((obs, old, value) ->
                detailTab.showSelection(toSelection(value), editorActions()));

        configureHistoryTable();
        configureGlobalEnvironmentTable();

        VBox treePanel = new VBox(tree);
        treePanel.getStyleClass().add("sidebar-panel");
        tree.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            if (event.getTarget() != tree) return;
            showContextMenu(TreeContextMenus.forEmptyArea(this), tree, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        VBox.setVgrow(tree, Priority.ALWAYS);
        projectWorkspace = new SplitPane(treePanel, detailTab.root());
        projectWorkspace.setOrientation(Orientation.HORIZONTAL);
        projectWorkspace.setDividerPositions(0.25);

        globalEnvironmentView = buildGlobalEnvironmentView();
        executionHistoryView = buildExecutionHistoryView();

        centerStack = new StackPane(projectWorkspace, globalEnvironmentView, executionHistoryView);
        showCenterView(CenterView.PROJECT);
        root.setCenter(centerStack);
        HBox statusBar = new HBox(status);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.getStyleClass().add("status-bar");
        root.setBottom(statusBar);
        root.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> dismissContextMenu());
        detailTab.root().addEventFilter(MouseEvent.MOUSE_PRESSED, event -> dismissContextMenu());
    }

    private void showContextMenu(ContextMenu menu, Node owner, double screenX, double screenY) {
        dismissContextMenu();
        activeContextMenu = menu;
        menu.setAutoHide(true);
        menu.setOnHidden(e -> {
            if (activeContextMenu == menu) activeContextMenu = null;
        });
        menu.show(owner, screenX, screenY);
    }

    private void dismissContextMenu() {
        if (activeContextMenu != null && activeContextMenu.isShowing()) {
            activeContextMenu.hide();
        }
    }

    private HBox headerBar() {
        MenuButton projectMenu = new MenuButton("项目管理");
        projectMenu.getItems().addAll(
                UiIcons.menuItem(Feather.FOLDER, "打开", this::openProjectManagement),
                UiIcons.menuItem(Feather.FOLDER_PLUS, "新建项目", this::createProjectDialog),
                new SeparatorMenuItem(),
                UiIcons.menuItem(Feather.SAVE, "备份数据库", this::backup));
        Button globalEnv = navButton("全局环境变量", this::showGlobalEnvironment);
        Button history = navButton("执行历史", this::showExecutionHistory);
        Button about = navButton("关于", EditorDialogs::showAbout);
        Region menuSpacer = new Region();
        HBox.setHgrow(menuSpacer, Priority.ALWAYS);
        HBox menuBar = new HBox(4, projectMenu, globalEnv, history, about, menuSpacer, themeToggle);
        menuBar.getStyleClass().add("app-menu-bar");
        menuBar.setAlignment(Pos.CENTER_LEFT);
        menuBar.getStyleClass().add("app-header");
        return menuBar;
    }

    private Button navButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void openProjectManagement() {
        showCenterView(CenterView.PROJECT);
        refreshTree();
        status.setText("项目管理");
    }

    private void createProjectDialog() {
        EditorDialogs.nameDialog("新建项目", "新项目", "").ifPresent(values -> {
            try {
                if (values[0].isBlank()) throw new IllegalArgumentException("名称不能为空");
                Project project = projectService.save(null, values[0], values[1]);
                showCenterView(CenterView.PROJECT);
                refreshTree();
                selectProject(project.id());
                detailTab.showProjectDetail(toSelection(findProjectItem(project.id())), editorActions());
                showCenterView(CenterView.PROJECT);
                status.setText("项目已创建");
            } catch (Exception e) {
                fail(e);
            }
        });
    }

    private void showGlobalEnvironment() {
        showCenterView(CenterView.GLOBAL_ENV);
        refreshGlobalEnvironment();
        status.setText("全局环境变量");
    }

    private void showExecutionHistory() {
        showCenterView(CenterView.HISTORY);
        refreshHistory();
        status.setText("执行历史");
    }

    private void showCenterView(CenterView view) {
        projectWorkspace.setVisible(view == CenterView.PROJECT);
        projectWorkspace.setManaged(view == CenterView.PROJECT);
        globalEnvironmentView.setVisible(view == CenterView.GLOBAL_ENV);
        globalEnvironmentView.setManaged(view == CenterView.GLOBAL_ENV);
        executionHistoryView.setVisible(view == CenterView.HISTORY);
        executionHistoryView.setManaged(view == CenterView.HISTORY);
    }

    private VBox buildGlobalEnvironmentView() {
        Label title = sectionLabel("全局环境变量");
        Label hint = new Label("""
                持久化存储于本地 SQLite；application.yml 中 workflowtest.global.* 为默认兜底，数据库同名变量优先。
                运行时通过 ${global.变量名} 引用。""");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        Button refresh = UiIcons.textButton(Feather.REFRESH_CW, "刷新", this::refreshGlobalEnvironment);
        Button add = UiIcons.textButton(Feather.PLUS, "新增", this::addGlobalVariable);
        globalEnvTable.setPlaceholder(new Label("尚未配置全局环境变量"));
        globalEnvTable.setRowFactory(view -> {
            TableRow<GlobalVariable> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) editGlobalVariable(row.getItem());
            });
            return row;
        });
        VBox.setVgrow(globalEnvTable, Priority.ALWAYS);
        VBox panel = new VBox(12, title, hint, new HBox(8, add, refresh), globalEnvTable);
        panel.getStyleClass().add("content-panel");
        panel.setPadding(new javafx.geometry.Insets(18));
        return panel;
    }

    private VBox buildExecutionHistoryView() {
        Label title = sectionLabel("执行历史");
        Button refresh = UiIcons.textButton(Feather.REFRESH_CW, "刷新", this::refreshHistory);
        historyTable.setPlaceholder(new Label("暂无执行记录"));
        VBox.setVgrow(historyTable, Priority.ALWAYS);
        VBox panel = new VBox(12, title, refresh, historyTable);
        panel.getStyleClass().add("content-panel");
        panel.setPadding(new javafx.geometry.Insets(18));
        return panel;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private void configureGlobalEnvironmentTable() {
        globalEnvTable = new TableView<>();
        TableColumn<GlobalVariable, String> keyCol = new TableColumn<>("变量名");
        keyCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().key()));
        TableColumn<GlobalVariable, String> valueCol = new TableColumn<>("值");
        valueCol.setCellValueFactory(cell -> new SimpleStringProperty(
                EditorForms.json(objectMapper, cell.getValue().value())));
        TableColumn<GlobalVariable, String> refCol = new TableColumn<>("引用");
        refCol.setCellValueFactory(cell -> new SimpleStringProperty("${global." + cell.getValue().key() + "}"));
        globalEnvTable.getColumns().addAll(keyCol, valueCol, refCol);
        globalEnvTable.getColumns().add(UiIcons.actionsColumn(this::editGlobalVariable, this::deleteGlobalVariable));
        globalEnvTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void refreshGlobalEnvironment() {
        try {
            globalEnvTable.setItems(FXCollections.observableArrayList(globalVariables.list()));
        } catch (Exception e) {
            fail(e);
        }
    }

    private void addGlobalVariable() {
        EditorDialogs.globalVariableDialog(null, objectMapper).ifPresent(v -> {
            globalVariables.save(v);
            refreshGlobalEnvironment();
            status.setText("全局环境变量已保存");
        });
    }

    private void editGlobalVariable(GlobalVariable selected) {
        EditorDialogs.globalVariableDialog(selected, objectMapper).ifPresent(v -> {
            globalVariables.save(v);
            refreshGlobalEnvironment();
            status.setText("全局环境变量已保存");
        });
    }

    private void deleteGlobalVariable(GlobalVariable selected) {
        if (!EditorDialogs.confirm("确认删除全局变量「" + selected.key() + "」？")) return;
        globalVariables.delete(selected.id());
        refreshGlobalEnvironment();
        status.setText("全局环境变量已删除");
    }

    private void selectProject(Long projectId) {
        TreeItem<NodeRef> item = findProjectItem(projectId);
        if (item != null) tree.getSelectionModel().select(item);
    }

    private TreeItem<NodeRef> findProjectItem(Long projectId) {
        if (tree.getRoot() == null || projectId == null) return null;
        for (TreeItem<NodeRef> child : tree.getRoot().getChildren()) {
            if (child.getValue() != null && child.getValue().type() == NodeType.PROJECT
                    && Objects.equals(child.getValue().id(), projectId)) {
                return child;
            }
        }
        return null;
    }

    private void configureTreeContextMenu() {
        tree.setCellFactory(view -> {
            TreeCell<NodeRef> cell = new TreeCell<>() {
                @Override
                protected void updateItem(NodeRef item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name());
                }
            };
            cell.setOnContextMenuRequested(event -> {
                if (cell.isEmpty() || cell.getItem() == null) return;
                tree.getSelectionModel().select(cell.getTreeItem());
                showContextMenu(TreeContextMenus.forNode(cell.getItem(), MainWindow.this),
                        cell, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            cell.setOnMousePressed(event -> {
                if (event.isPrimaryButtonDown()) dismissContextMenu();
            });
            cell.setOnMouseClicked(event -> {
                if (event.getClickCount() != 2 || cell.isEmpty() || cell.getItem() == null) return;
                tree.getSelectionModel().select(cell.getTreeItem());
                DetailTabPanel.Selection selection = toSelection(cell.getTreeItem());
                if (cell.getItem().type() == NodeType.PROJECT) {
                    detailTab.showProjectDetail(selection, editorActions());
                } else {
                    detailTab.showSelection(selection, editorActions());
                }
            });
            return cell;
        });
    }

    private void toggleTheme() {
        AppTheme.toggle();
        themeToggle.setGraphic(UiIcons.icon(UiIcons.themeIcon(), 16));
        themeToggle.setTooltip(new Tooltip(AppTheme.isDarkMode() ? "切换为浅色主题" : "切换为深色主题"));
    }

    private DetailTabPanel.EditorActions editorActions() {
        return new DetailTabPanel.EditorActions() {
            @Override public void refreshTree() { MainWindow.this.refreshTree(); }
            @Override public void selectEditTab() { showCenterView(CenterView.PROJECT); }
            @Override public void onStatus(String message) { status.setText(message); }
            @Override public void fail(Throwable error) { MainWindow.this.fail(error); }
            @Override public void showInfo(String title, String message) { EditorDialogs.showInfo(title, message); }
            @Override public boolean confirm(String message) { return EditorDialogs.confirm(message); }
        };
    }

    @Override
    public void refreshTree() {
        TreeItem<NodeRef> selected = tree.getSelectionModel().getSelectedItem();
        try {
            TreeItem<NodeRef> rootItem = new TreeItem<>(new NodeRef(NodeType.ROOT, null, "测试工程", null, null, null, null));
            rootItem.setExpanded(true);
            for (ProjectNode projectNode : projectTree.loadTree().projects()) {
                Project project = projectNode.project();
                TreeItem<NodeRef> projectItem = new TreeItem<>(new NodeRef(NodeType.PROJECT, project.id(), project.name(),
                        project.id(), null, null, project));
                projectItem.setExpanded(true);
                for (GroupNode groupNode : projectNode.groups()) {
                    Group group = groupNode.group();
                    TreeItem<NodeRef> groupItem = new TreeItem<>(new NodeRef(NodeType.GROUP, group.id(), group.name(),
                            project.id(), group.id(), null, group));
                    groupItem.setExpanded(true);
                    addGroupHookNodes(groupItem, group.id(), project.id(), HookType.BEFORE_GROUP);
                    for (WorkflowNode workflowNode : groupNode.workflows()) {
                        Workflow workflow = workflowNode.workflow();
                        TreeItem<NodeRef> workflowItem = new TreeItem<>(new NodeRef(NodeType.WORKFLOW, workflow.id(), workflow.name(),
                                project.id(), group.id(), workflow.id(), workflow));
                        workflowItem.setExpanded(true);
                        for (Step step : workflowNode.steps()) {
                            workflowItem.getChildren().add(new TreeItem<>(new NodeRef(NodeType.STEP, step.id(),
                                    step.sortOrder() + ". " + step.name() + " [" + step.type() + "]",
                                    project.id(), group.id(), workflow.id(), step)));
                        }
                        groupItem.getChildren().add(workflowItem);
                    }
                    addGroupHookNodes(groupItem, group.id(), project.id(), HookType.AFTER_GROUP);
                    projectItem.getChildren().add(groupItem);
                }
                rootItem.getChildren().add(projectItem);
            }
            tree.setRoot(rootItem);
            if (selected != null) {
                reselect(rootItem, selected.getValue());
            }
            status.setText("工程树已刷新");
            detailTab.showSelection(toSelection(tree.getSelectionModel().getSelectedItem()), editorActions());
        } catch (Exception e) { fail(e); }
    }

    private void reselect(TreeItem<NodeRef> rootItem, NodeRef target) {
        if (target == null || target.type() == NodeType.ROOT) return;
        TreeItem<NodeRef> found = findNode(rootItem, target.id(), target.type());
        if (found != null) tree.getSelectionModel().select(found);
    }

    private TreeItem<NodeRef> findNode(TreeItem<NodeRef> item, Long id, NodeType type) {
        if (item.getValue() != null && item.getValue().type() == type && java.util.Objects.equals(id, item.getValue().id())) {
            return item;
        }
        for (TreeItem<NodeRef> child : item.getChildren()) {
            TreeItem<NodeRef> found = findNode(child, id, type);
            if (found != null) return found;
        }
        return null;
    }

    private void addGroupHookNodes(TreeItem<NodeRef> groupItem, Long groupId, Long projectId, HookType hookType) {
        for (Hook hook : hookDefinitions.listByGroup(groupId)) {
            if (hook.hookType() != hookType) continue;
            String label = hookType == HookType.BEFORE_GROUP ? "组前置钩子" : "组后置钩子";
            TreeItem<NodeRef> hookItem = new TreeItem<>(new NodeRef(NodeType.HOOK, hook.id(), label,
                    projectId, groupId, null, hook));
            for (Step step : hook.steps()) {
                hookItem.getChildren().add(new TreeItem<>(new NodeRef(NodeType.HOOK_STEP, step.id(),
                        step.sortOrder() + ". " + step.name() + " [" + step.type() + "]",
                        projectId, groupId, null, step)));
            }
            groupItem.getChildren().add(hookItem);
        }
    }

    private DetailTabPanel.Selection toSelection(TreeItem<NodeRef> item) {
        if (item == null || item.getValue() == null) return null;
        NodeRef ref = item.getValue();
        return new DetailTabPanel.Selection(
                switch (ref.type()) {
                    case ROOT -> DetailTabPanel.SelectionKind.ROOT;
                    case PROJECT -> DetailTabPanel.SelectionKind.PROJECT;
                    case GROUP -> DetailTabPanel.SelectionKind.GROUP;
                    case WORKFLOW -> DetailTabPanel.SelectionKind.WORKFLOW;
                    case HOOK -> DetailTabPanel.SelectionKind.HOOK;
                    case STEP -> DetailTabPanel.SelectionKind.STEP;
                    case HOOK_STEP -> DetailTabPanel.SelectionKind.HOOK_STEP;
                },
                ref.id(), ref.name(), ref.projectId(), ref.groupId(), ref.workflowId(), ref.value());
    }

    @Override
    public void addGroup(NodeRef context) {
        Long projectId = context.type() == NodeType.PROJECT ? context.id() : context.projectId();
        if (projectId == null) { EditorDialogs.showError("请在项目上新建组"); return; }
        detailTab.showCreate(new DetailTabPanel.CreateRequest(DetailTabPanel.SelectionKind.GROUP,
                projectId, null, null, null), editorActions());
        showCenterView(CenterView.PROJECT);
    }

    @Override
    public void addWorkflow(NodeRef context) {
        Long groupId = context.type() == NodeType.GROUP ? context.id() : context.groupId();
        if (groupId == null) { EditorDialogs.showError("请在组上新建工作流"); return; }
        detailTab.showCreate(new DetailTabPanel.CreateRequest(DetailTabPanel.SelectionKind.WORKFLOW,
                context.projectId(), groupId, null, null), editorActions());
        showCenterView(CenterView.PROJECT);
    }

    @Override
    public void addStep(NodeRef context) {
        Long workflowId = context.type() == NodeType.WORKFLOW ? context.id() : context.workflowId();
        if (workflowId == null) { EditorDialogs.showError("请在工作流上新建步骤"); return; }
        detailTab.showCreate(new DetailTabPanel.CreateRequest(DetailTabPanel.SelectionKind.STEP,
                context.projectId(), context.groupId(), workflowId, workflowId), editorActions());
        showCenterView(CenterView.PROJECT);
    }

    @Override
    public void addHookStep(NodeRef context) {
        if (context.type() != NodeType.HOOK) {
            EditorDialogs.showError("请在钩子节点上新建钩子步骤");
            return;
        }
        detailTab.showCreate(new DetailTabPanel.CreateRequest(DetailTabPanel.SelectionKind.HOOK_STEP,
                context.projectId(), context.groupId(), null, context.id()), editorActions());
        showCenterView(CenterView.PROJECT);
    }

    @Override
    public void addGroupHookStep(NodeRef context, HookType hookType) {
        Long groupId = context.type() == NodeType.GROUP ? context.id() : context.groupId();
        if (groupId == null) {
            EditorDialogs.showError("请在组上新建钩子步骤");
            return;
        }
        Hook hook = hookDefinitions.find(groupId, hookType).orElse(null);
        if (hook == null) {
            EditorDialogs.showError(hookType == HookType.BEFORE_GROUP ? "组前置钩子不存在" : "组后置钩子不存在");
            return;
        }
        detailTab.showCreate(new DetailTabPanel.CreateRequest(DetailTabPanel.SelectionKind.HOOK_STEP,
                context.projectId(), groupId, null, hook.id()), editorActions());
        showCenterView(CenterView.PROJECT);
    }

    @Override
    public void runNode(NodeRef context) {
        runSelected(context);
    }

    @Override
    public void moveNode(NodeRef context, int delta) {
        try {
            switch (context.type()) {
                case GROUP -> workflowGroups.move(context.id(), delta);
                case WORKFLOW -> workflowDefinitions.move(context.id(), delta);
                case STEP, HOOK_STEP -> {
                    Step step = (Step) context.value();
                    stepDefinitions.move(context.id(), step.hookStep(), delta);
                }
                default -> throw new IllegalArgumentException("当前节点不可排序");
            }
            refreshTree();
        } catch (Exception e) { fail(e); }
    }

    @Override
    public void deleteNode(NodeRef context) {
        if (!EditorDialogs.confirm("确认删除「" + context.name() + "」？")) return;
        try {
            switch (context.type()) {
                case PROJECT -> projectService.delete(context.id());
                case GROUP -> workflowGroups.delete(context.id());
                case WORKFLOW -> workflowDefinitions.delete(context.id());
                case STEP -> stepDefinitions.delete(context.id(), false);
                case HOOK_STEP -> stepDefinitions.delete(context.id(), true);
                default -> throw new IllegalArgumentException("当前节点不可删除");
            }
            detailTab.showEmpty();
            refreshTree();
            status.setText("已删除");
        } catch (Exception e) { fail(e); }
    }

    @Override
    public void openEditTab() {
        TreeItem<NodeRef> item = tree.getSelectionModel().getSelectedItem();
        if (item != null && item.getValue() != null && item.getValue().type() == NodeType.PROJECT) {
            detailTab.showProjectDetail(toSelection(item), editorActions());
        }
        showCenterView(CenterView.PROJECT);
    }

    @Override
    public void stopExecution() {
        if (activeExecution != null) {
            executionControl.cancel(activeExecution.executionId());
            status.setText("正在停止...");
        }
    }

    private void runSelected(NodeRef ref) {
        if (ref == null) return;
        ExecutionListener listener = event -> { };
        if (ref.type() == NodeType.PROJECT) activeExecution = projectExecutions.submit(
                new ProjectExecutionCommand(ref.id(), Map.of()), listener);
        else if (ref.type() == NodeType.WORKFLOW) activeExecution = workflowRuns.submit(
                new WorkflowExecutionCommand(ref.id(), Map.of()), listener);
        else if (ref.type() == NodeType.GROUP) activeExecution = groupExecutions.submit(
                new GroupExecutionCommand(ref.id(), Map.of()), listener);
        else if (ref.workflowId() != null) activeExecution = workflowRuns.submit(
                new WorkflowExecutionCommand(ref.workflowId(), Map.of()), listener);
        else if (ref.groupId() != null) activeExecution = groupExecutions.submit(
                new GroupExecutionCommand(ref.groupId(), Map.of()), listener);
        else { EditorDialogs.showError("请选择项目、组或工作流"); return; }
        status.setText("执行中：" + ref.name());
        activeExecution.future().whenComplete((result, error) -> Platform.runLater(() -> {
            if (error != null) status.setText("执行异常：" + error.getMessage());
            else status.setText("执行完成：" + result.status() + "，耗时 " + result.elapsedMs() + "ms");
            refreshHistory();
            activeExecution = null;
        }));
    }

    private void configureHistoryTable() {
        historyTable.getColumns().addAll(
                column("类型", ExecutionSummary::type),
                column("目标", ExecutionSummary::targetName),
                column("状态", ExecutionSummary::status),
                column("开始时间", e -> e.startedAt() == null ? "" : e.startedAt().toString()),
                column("耗时(ms)", e -> String.valueOf(e.elapsedMs())),
                column("错误", e -> nullSafe(e.errorMessage())));
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        historyTable.setRowFactory(table -> {
            TableRow<ExecutionSummary> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) showExecutionDetails(row.getItem());
            });
            return row;
        });
    }

    private void showExecutionDetails(ExecutionSummary summary) {
        EditorDialogs.executionDetails(summary, stepExecutions.listByExecution(summary.id()), objectMapper);
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
        try { historyTable.setItems(FXCollections.observableArrayList(executionHistory.recent(100))); }
        catch (Exception e) { fail(e); }
    }

    private <T> TableColumn<T, String> column(String name, java.util.function.Function<T, String> value) {
        TableColumn<T, String> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        return column;
    }

    private NodeRef selected() { TreeItem<NodeRef> item = tree.getSelectionModel().getSelectedItem(); return item == null ? null : item.getValue(); }
    private void fail(Throwable e) { status.setText("失败：" + e.getMessage()); EditorDialogs.showError(e.getMessage() == null ? e.toString() : e.getMessage()); }
    private String nullSafe(String value) { return value == null ? "" : value; }

    enum NodeType { ROOT, PROJECT, GROUP, WORKFLOW, HOOK, STEP, HOOK_STEP }
    record NodeRef(NodeType type, Long id, String name, Long projectId,
                   Long groupId, Long workflowId, Object value) {
        @Override public String toString() { return name; }
    }


}
