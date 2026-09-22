package com.workflowtest.desktop.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import javafx.application.Application;

public final class AppTheme {
    private static Theme current = new PrimerLight();

    private AppTheme() {}

    public static void applyDefault() {
        apply(new PrimerLight());
    }

    public static void apply(Theme theme) {
        current = theme;
        Application.setUserAgentStylesheet(theme.getUserAgentStylesheet());
    }

    public static void toggle() {
        apply(current.isDarkMode() ? new PrimerLight() : new PrimerDark());
    }

    public static boolean isDarkMode() {
        return current.isDarkMode();
    }
}
