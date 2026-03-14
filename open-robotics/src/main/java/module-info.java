module com.openrobotics {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires javafx.base;
    requires com.fasterxml.jackson.databind;
    requires com.zaxxer.hikari;
    requires flyway.core;
    requires javafx.swing;
    requires java.sql;
    requires java.prefs;
    requires java.desktop;

    opens com.openrobotics to javafx.fxml;
    opens com.openrobotics.controllers to javafx.fxml;
    opens com.openrobotics.io to com.fasterxml.jackson.databind; // Jackson needs reflective access to deserialize DTOs
    opens com.openrobotics.task to com.fasterxml.jackson.databind;

    exports com.openrobotics;
    exports com.openrobotics.controllers;
    exports com.openrobotics.util;
}

