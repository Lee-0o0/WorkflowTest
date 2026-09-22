package com.workflowtest.desktop.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.function.Consumer;

final class UiIcons {
    private UiIcons() {}

    static FontIcon icon(Ikon ikon, int size) {
        FontIcon graphic = new FontIcon(ikon);
        graphic.setIconSize(size);
        return graphic;
    }

    static Button iconButton(Ikon ikon, String tooltip, Runnable action, String... styleClasses) {
        Button button = new Button();
        button.setGraphic(icon(ikon, 16));
        button.setTooltip(new Tooltip(tooltip));
        button.getStyleClass().add("flat");
        for (String styleClass : styleClasses) button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }

    static Button textButton(Ikon ikon, String text, Runnable action, String... styleClasses) {
        Button button = new Button(text, icon(ikon, 14));
        for (String styleClass : styleClasses) button.getStyleClass().add(styleClass);
        button.setOnAction(event -> action.run());
        return button;
    }

    static MenuItem menuItem(Ikon ikon, String text, Runnable action) {
        MenuItem item = new MenuItem(text, icon(ikon, 14));
        item.setOnAction(event -> action.run());
        return item;
    }

    static Ikon themeIcon() {
        return AppTheme.isDarkMode() ? Feather.SUN : Feather.MOON;
    }

    static <T> TableColumn<T, Void> actionsColumn(Consumer<T> onEdit, Consumer<T> onDelete) {
        TableColumn<T, Void> column = new TableColumn<>("操作");
        column.setPrefWidth(120);
        column.setMaxWidth(160);
        column.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = tableActionButton("编辑");
            private final Button deleteBtn = tableActionButton("删除", "danger");
            private final HBox box = new HBox(8, editBtn, deleteBtn);

            {
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    T row = getTableRow().getItem();
                    editBtn.setOnAction(e -> onEdit.accept(row));
                    deleteBtn.setOnAction(e -> onDelete.accept(row));
                    setGraphic(box);
                }
            }
        });
        return column;
    }

    private static Button tableActionButton(String text, String... styleClasses) {
        Button button = new Button(text);
        button.getStyleClass().add("table-action-button");
        for (String styleClass : styleClasses) button.getStyleClass().add(styleClass);
        return button;
    }
}
