module com.openrobotics {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires javafx.charts;
    requires javafx.swing;
    requires java.sql;
    requires java.prefs;
    requires java.desktop;

    opens com.openrobotics to javafx.fxml;
    opens com.openrobotics.controllers to javafx.fxml;
    opens com.openrobotics.model to javafx.fxml, javafx.base;

    exports com.openrobotics;
    exports com.openrobotics.controllers;
    exports com.openrobotics.model;
    exports com.openrobotics.util;
}

