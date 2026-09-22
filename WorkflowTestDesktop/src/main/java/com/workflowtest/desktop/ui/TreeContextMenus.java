package com.workflowtest.desktop.ui;

import com.workflowtest.engine.api.definition.DefinitionModels.HookType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import org.kordamp.ikonli.feather.Feather;

final class TreeContextMenus {
    interface Host {
        void addGroup(MainWindow.NodeRef context);
        void addWorkflow(MainWindow.NodeRef context);
        void addStep(MainWindow.NodeRef context);
        void addHookStep(MainWindow.NodeRef context);
        void addGroupHookStep(MainWindow.NodeRef context, HookType hookType);
        void runNode(MainWindow.NodeRef context);
        void stopExecution();
        void moveNode(MainWindow.NodeRef context, int delta);
        void deleteNode(MainWindow.NodeRef context);
        void openEditTab();
        void refreshTree();
    }

    private TreeContextMenus() {}

    static ContextMenu forEmptyArea(Host host) {
        ContextMenu menu = new ContextMenu();
        menu.getItems().add(item(Feather.REFRESH_CW, "刷新", host::refreshTree));
        return menu;
    }

    static ContextMenu forNode(MainWindow.NodeRef ref, Host host) {
        ContextMenu menu = new ContextMenu();
        switch (ref.type()) {
            case PROJECT -> menu.getItems().addAll(
                    item(Feather.PLAY, "运行项目", () -> host.runNode(ref)),
                    item(Feather.SQUARE, "停止", host::stopExecution),
                    item(Feather.FOLDER, "新建组", () -> host.addGroup(ref)),
                    new SeparatorMenuItem(),
                    item(Feather.EDIT_2, "项目详情", host::openEditTab),
                    item(Feather.TRASH_2, "删除项目", () -> host.deleteNode(ref)));
            case GROUP -> menu.getItems().addAll(
                    item(Feather.FOLDER, "新建组", () -> host.addGroup(ref)),
                    item(Feather.GIT_BRANCH, "新建工作流", () -> host.addWorkflow(ref)),
                    item(Feather.LINK, "新建组前置钩子步骤", () -> host.addGroupHookStep(ref, HookType.BEFORE_GROUP)),
                    item(Feather.LINK, "新建组后置钩子步骤", () -> host.addGroupHookStep(ref, HookType.AFTER_GROUP)),
                    item(Feather.PLAY, "运行组", () -> host.runNode(ref)),
                    item(Feather.SQUARE, "停止", host::stopExecution),
                    new SeparatorMenuItem(),
                    item(Feather.ARROW_UP, "上移", () -> host.moveNode(ref, -1)),
                    item(Feather.ARROW_DOWN, "下移", () -> host.moveNode(ref, 1)),
                    new SeparatorMenuItem(),
                    item(Feather.EDIT_2, "编辑", host::openEditTab),
                    item(Feather.TRASH_2, "删除组", () -> host.deleteNode(ref)));
            case WORKFLOW -> menu.getItems().addAll(
                    item(Feather.LIST, "新建步骤", () -> host.addStep(ref)),
                    item(Feather.PLAY, "运行工作流", () -> host.runNode(ref)),
                    item(Feather.SQUARE, "停止", host::stopExecution),
                    new SeparatorMenuItem(),
                    item(Feather.ARROW_UP, "上移", () -> host.moveNode(ref, -1)),
                    item(Feather.ARROW_DOWN, "下移", () -> host.moveNode(ref, 1)),
                    new SeparatorMenuItem(),
                    item(Feather.EDIT_2, "编辑", host::openEditTab),
                    item(Feather.TRASH_2, "删除工作流", () -> host.deleteNode(ref)));
            case HOOK -> menu.getItems().addAll(
                    item(Feather.LIST, "新建钩子步骤", () -> host.addHookStep(ref)),
                    item(Feather.EDIT_2, "编辑", host::openEditTab));
            case STEP, HOOK_STEP -> menu.getItems().addAll(
                    item(Feather.EDIT_2, "编辑", host::openEditTab),
                    item(Feather.ARROW_UP, "上移", () -> host.moveNode(ref, -1)),
                    item(Feather.ARROW_DOWN, "下移", () -> host.moveNode(ref, 1)),
                    new SeparatorMenuItem(),
                    item(Feather.TRASH_2, "删除步骤", () -> host.deleteNode(ref)));
            default -> { }
        }
        return menu;
    }

    private static MenuItem item(Feather icon, String text, Runnable action) {
        return UiIcons.menuItem(icon, text, action);
    }
}
