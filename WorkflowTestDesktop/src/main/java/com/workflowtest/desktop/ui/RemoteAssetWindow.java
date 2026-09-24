package com.workflowtest.desktop.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.desktop.execution.ExecutionEventSink;
import com.workflowtest.desktop.remote.ServerClient;
import com.workflowtest.engine.api.execution.ExecutionControlService;
import com.workflowtest.engine.api.execution.ExecutionModels.*;
import com.workflowtest.engine.listener.ExecutionEvent;
import com.workflowtest.engine.api.execution.PackageExecutionService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Component
public class RemoteAssetWindow {
    private final ServerClient server;
    private final PackageExecutionService packageExecutions;
    private final ExecutionControlService executionControl;
    private final ExecutionEventSink executionEventSink;
    private final ObjectMapper json;
    private final TreeView<RemoteNode> tree = new TreeView<>();
    private final TextArea details = new TextArea();
    private final TextArea log = new TextArea();
    private final Label status = new Label("未连接");
    private Stage stage;
    private ExecutionHandle active;

    public RemoteAssetWindow(ServerClient server, PackageExecutionService packageExecutions,
                             ExecutionControlService executionControl, ExecutionEventSink executionEventSink,
                             ObjectMapper json) {
        this.server = server;
        this.packageExecutions = packageExecutions;
        this.executionControl = executionControl;
        this.executionEventSink = executionEventSink;
        this.json = json;
    }

    public void show(Window owner) {
        if (stage != null) { stage.show(); stage.toFront(); return; }
        stage = new Stage(); stage.initOwner(owner); stage.initModality(Modality.NONE); stage.setTitle("WorkflowTest - 集中测试资产");
        BorderPane root = new BorderPane(); root.setTop(toolbar());
        tree.setPrefWidth(360); tree.setShowRoot(true);
        tree.getSelectionModel().selectedItemProperty().addListener((o, old, value) -> showDetails(value));
        details.setEditable(false); details.setStyle("-fx-font-family: Consolas;");
        log.setEditable(false); log.setStyle("-fx-font-family: Consolas;");
        TabPane tabs = new TabPane(tab("资产详情", details), tab("本地执行日志", log));
        SplitPane split = new SplitPane(tree, tabs); split.setDividerPositions(0.28); root.setCenter(split);
        HBox bottom = new HBox(status); bottom.setPadding(new Insets(6, 10, 6, 10)); root.setBottom(bottom);
        Scene scene = new Scene(root, 1280, 780);
        var css = getClass().getResource("/styles/main.css"); if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene); stage.setOnHidden(e -> { if (active != null) executionControl.cancel(active.executionId()); stage = null; }); stage.show();
        login();
    }

    private ToolBar toolbar() {
        return new ToolBar(button("连接/切换用户", this::login), button("刷新", this::refresh),
                new Separator(), button("新建项目", this::addProject), button("新建组", this::addGroup),
                button("新建工作流", this::addWorkflow), button("编辑工作流定义", this::editDraft),
                button("环境变量", this::addVariable), new Separator(), button("发布", this::publish),
                button("运行已发布版本", this::run), button("停止", this::stop), new Separator(),
                button("用户管理", this::manageUsers), button("项目成员", this::manageMembers));
    }

    private void login() {
        Dialog<String[]> dialog = new Dialog<>(); dialog.setTitle("连接集中资产服务");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField url = new TextField(server.baseUrl()); TextField username = new TextField("admin"); PasswordField password = new PasswordField();
        GridPane grid = grid(); grid.addRow(0, new Label("服务地址"), url); grid.addRow(1, new Label("用户名"), username); grid.addRow(2, new Label("密码"), password);
        dialog.getDialogPane().setContent(grid); dialog.setResultConverter(b -> b == ButtonType.OK ? new String[]{url.getText(), username.getText(), password.getText()} : null);
        dialog.showAndWait().ifPresent(values -> {
            server.configure(values[0]); status.setText("正在登录...");
            async(() -> server.login(values[1], values[2]), user -> {
                status.setText("已连接 " + server.baseUrl() + "，用户：" + user.path("displayName").asText()); refresh();
            });
        });
    }

    private void refresh() {
        if (!server.connected()) { error("请先连接服务端"); return; }
        status.setText("正在加载集中资产...");
        async(this::loadTree, root -> { tree.setRoot(root); status.setText("集中资产已刷新"); });
    }

    private TreeItem<RemoteNode> loadTree() {
        TreeItem<RemoteNode> root = new TreeItem<>(new RemoteNode(Type.ROOT, "root", "公司测试资产", null, null, null, null)); root.setExpanded(true);
        for (JsonNode project : server.projects()) {
            String projectId = project.path("id").asText();
            TreeItem<RemoteNode> pi = item(Type.PROJECT, projectId, project.path("name").asText(), projectId, null, null, project); pi.setExpanded(true);
            for (JsonNode group : server.groups(projectId)) {
                String groupId = group.path("id").asText();
                TreeItem<RemoteNode> gi = item(Type.GROUP, groupId, group.path("name").asText(), projectId, groupId, null, group); gi.setExpanded(true);
                for (JsonNode workflow : server.workflows(groupId)) {
                    String workflowId = workflow.path("id").asText();
                    gi.getChildren().add(item(Type.WORKFLOW, workflowId, workflow.path("name").asText(), projectId, groupId, workflowId, workflow));
                }
                pi.getChildren().add(gi);
            }
            root.getChildren().add(pi);
        }
        return root;
    }

    private void showDetails(TreeItem<RemoteNode> item) {
        if (item == null || item.getValue().value() == null) { details.clear(); return; }
        details.setText(pretty(item.getValue().value()));
    }

    private void addProject() {
        nameDialog("新建集中项目", true).ifPresent(v -> async(() -> server.createProject(v[0], v[1], v[2]), x -> refresh()));
    }
    private void addGroup() {
        RemoteNode node = selected(); if (node == null || node.projectId() == null) { error("请选择项目"); return; }
        nameDialog("新建组", false).ifPresent(v -> async(() -> server.createGroup(node.projectId(), v[1], v[2]), x -> refresh()));
    }
    private void addWorkflow() {
        RemoteNode node = selected(); if (node == null || node.groupId() == null) { error("请选择组"); return; }
        nameDialog("新建工作流", false).ifPresent(v -> async(() -> server.createWorkflow(node.groupId(), v[1], v[2]), x -> refresh()));
    }

    private void editDraft() {
        RemoteNode node = selected(); if (node == null || node.type() != Type.WORKFLOW) { error("请选择工作流"); return; }
        JsonNode workflow = node.value(); Dialog<JsonNode> dialog = new Dialog<>(); dialog.setTitle("编辑工作流定义（revision " + workflow.path("revision").asInt() + "）");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextArea editor = new TextArea(pretty(workflow.path("draft"))); editor.setStyle("-fx-font-family: Consolas;");
        dialog.getDialogPane().setContent(editor); dialog.getDialogPane().setPrefSize(900, 700);
        dialog.setResultConverter(b -> { if (b != ButtonType.OK) return null; try { return json.readTree(editor.getText()); } catch (Exception e) { error("JSON 格式错误：" + e.getMessage()); return null; } });
        dialog.showAndWait().ifPresent(draft -> async(() -> server.updateDraft(node.workflowId(), workflow.path("revision").asInt(), draft), value -> refresh()));
    }

    private void addVariable() {
        RemoteNode node = selected(); if (node == null || node.type() == Type.ROOT) { error("请选择项目、组或工作流"); return; }
        String scope = node.type() == Type.PROJECT ? "projects" : node.type() == Type.GROUP ? "groups" : "workflows";
        Dialog<String[]> dialog = new Dialog<>(); dialog.setTitle("新增环境变量"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField key = new TextField(); TextArea value = new TextArea("\"value\""); value.setPrefRowCount(5); GridPane grid = grid();
        grid.addRow(0, new Label("变量名"), key); grid.addRow(1, new Label("JSON 值"), value); dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(b -> b == ButtonType.OK ? new String[]{key.getText(), value.getText()} : null);
        dialog.showAndWait().ifPresent(v -> { try {
            JsonNode parsed = json.readTree(v[1]); async(() -> server.createVariable(scope, node.id(), v[0], parsed), x -> refresh());
        } catch (Exception e) { error("变量值必须是合法 JSON"); } });
    }

    private void publish() {
        RemoteNode node = selected(); if (node == null || node.type() != Type.WORKFLOW) { error("请选择工作流"); return; }
        async(() -> server.publish(node.workflowId()), version -> { status.setText("已发布版本 v" + version.path("version").asInt()); refresh(); });
    }

    private void run() {
        RemoteNode node = selected(); if (node == null || node.type() != Type.WORKFLOW) { error("请选择工作流"); return; }
        log.clear(); status.setText("正在下载执行包...");
        async(() -> {
            List<JsonNode> versions = server.versions(node.workflowId());
            if (versions.isEmpty()) throw new IllegalStateException("工作流尚未发布");
            return server.executionPackage(versions.getFirst().path("id").asText());
        }, pack -> {
            ExecutionEventSink.Subscription subscription = executionEventSink.subscribe(this::event);
            ExecutionHandle handle = packageExecutions.submit(new PackageExecutionCommand(pack, Map.of()));
            active = handle;
            status.setText("本地执行中：" + node.name());
            handle.future().whenComplete((result, throwable) -> Platform.runLater(() -> {
                subscription.close();
                if (throwable != null) { append("执行异常：" + throwable.getMessage()); status.setText("执行异常"); }
                else { append("完成：" + result.status() + "，耗时 " + result.elapsedMs() + "ms"); status.setText("执行完成：" + result.status()); }
                if (active == handle) active = null;
            }));
        });
    }
    private void stop() { if (active != null) executionControl.cancel(active.executionId()); }

    private void manageUsers() {
        async(server::users, users -> {
            Dialog<String[]> dialog = new Dialog<>(); dialog.setTitle("新增用户（仅系统管理员）"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            TextArea existing = new TextArea(pretty(json.valueToTree(users))); existing.setEditable(false); existing.setPrefRowCount(8);
            TextField username = new TextField(); TextField name = new TextField(); PasswordField password = new PasswordField(); ComboBox<String> role = new ComboBox<>(); role.getItems().setAll("USER", "ADMIN"); role.setValue("USER");
            VBox box = new VBox(8, new Label("现有用户"), existing, new Label("用户名"), username, new Label("显示名"), name, new Label("初始密码"), password, new Label("系统角色"), role); box.setPadding(new Insets(10));
            dialog.getDialogPane().setContent(box); dialog.setResultConverter(b -> b == ButtonType.OK ? new String[]{username.getText(), name.getText(), password.getText(), role.getValue()} : null);
            dialog.showAndWait().ifPresent(v -> async(() -> server.createUser(v[0], v[1], v[2], v[3]), x -> status.setText("用户已创建")));
        });
    }

    private void manageMembers() {
        RemoteNode node = selected(); if (node == null || node.projectId() == null) { error("请选择项目或其子节点"); return; }
        async(() -> List.of(server.users(), server.members(node.projectId())), data -> {
            List<JsonNode> users = data.get(0); List<JsonNode> members = data.get(1);
            Dialog<String[]> dialog = new Dialog<>(); dialog.setTitle("项目成员授权"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            TextArea existing = new TextArea(pretty(json.valueToTree(members))); existing.setEditable(false); existing.setPrefRowCount(8);
            ComboBox<JsonNode> user = new ComboBox<>(); user.getItems().setAll(users); user.setCellFactory(v -> userCell()); user.setButtonCell(userCell());
            ComboBox<String> role = new ComboBox<>(); role.getItems().setAll("PROJECT_ADMIN", "TEST_DEVELOPER", "TEST_EXECUTOR", "VIEWER"); role.setValue("TEST_EXECUTOR");
            VBox box = new VBox(8, new Label("现有成员"), existing, new Label("用户"), user, new Label("项目角色"), role); box.setPadding(new Insets(10)); dialog.getDialogPane().setContent(box);
            dialog.setResultConverter(b -> b == ButtonType.OK && user.getValue() != null ? new String[]{user.getValue().path("id").asText(), role.getValue()} : null);
            dialog.showAndWait().ifPresent(v -> async(() -> server.saveMember(node.projectId(), v[0], v[1]), x -> status.setText("项目成员权限已保存")));
        });
    }

    private ListCell<JsonNode> userCell() { return new ListCell<>() { @Override protected void updateItem(JsonNode item, boolean empty) { super.updateItem(item, empty); setText(empty || item == null ? null : item.path("displayName").asText() + " (" + item.path("username").asText() + ")"); } }; }
    private Optional<String[]> nameDialog(String title, boolean project) {
        Dialog<String[]> dialog = new Dialog<>(); dialog.setTitle(title); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextField key = new TextField("PROJECT"); TextField name = new TextField(); TextArea description = new TextArea(); description.setPrefRowCount(3); GridPane grid = grid(); int row = 0;
        if (project) grid.addRow(row++, new Label("项目编码"), key); grid.addRow(row++, new Label("名称"), name); grid.addRow(row, new Label("说明"), description); dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(b -> b == ButtonType.OK ? new String[]{key.getText().trim().toUpperCase(Locale.ROOT), name.getText().trim(), description.getText().trim()} : null); return dialog.showAndWait();
    }
    private void event(ExecutionEvent event) { Platform.runLater(() -> append(event.time().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS")) + "  " + event.type() + "  " + event.code() + "  " + event.message())); }
    private void append(String value) { log.appendText(value + System.lineSeparator()); }
    private RemoteNode selected() { TreeItem<RemoteNode> value = tree.getSelectionModel().getSelectedItem(); return value == null ? null : value.getValue(); }
    private TreeItem<RemoteNode> item(Type type, String id, String name, String project, String group, String workflow, JsonNode value) { return new TreeItem<>(new RemoteNode(type, id, name, project, group, workflow, value)); }
    private Button button(String text, Runnable action) { Button button = new Button(text); button.setOnAction(e -> action.run()); return button; }
    private Tab tab(String title, javafx.scene.Node node) { Tab tab = new Tab(title, node); tab.setClosable(false); return tab; }
    private GridPane grid() { GridPane grid = new GridPane(); grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(12)); return grid; }
    private String pretty(JsonNode node) { try { return json.writerWithDefaultPrettyPrinter().writeValueAsString(node); } catch (Exception e) { return String.valueOf(node); } }
    private void error(String message) { EditorDialogs.showError(message); status.setText("失败：" + message); }
    private <T> void async(Supplier<T> task, Consumer<T> success) {
        CompletableFuture.supplyAsync(task).whenComplete((value, throwable) -> Platform.runLater(() -> {
            if (throwable != null) { Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause(); error(cause.getMessage()); }
            else success.accept(value);
        }));
    }
    private enum Type { ROOT, PROJECT, GROUP, WORKFLOW }
    private record RemoteNode(Type type, String id, String name, String projectId, String groupId, String workflowId, JsonNode value) { @Override public String toString() { return name; } }
}
