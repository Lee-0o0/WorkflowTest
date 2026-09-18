package com.workflowtest.desktop;

import com.workflowtest.desktop.ui.MainWindow;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class WorkflowTestApplication extends Application {
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(DesktopConfiguration.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage stage) {
        MainWindow mainWindow = context.getBean(MainWindow.class);
        Scene scene = new Scene(mainWindow.root(), 1380, 840);
        scene.getStylesheets().add(getClass().getResource("/styles/main.css").toExternalForm());
        stage.setTitle("WorkflowTest - 本地工作台");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
