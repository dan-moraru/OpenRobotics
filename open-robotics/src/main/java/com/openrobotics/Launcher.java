package com.openrobotics;

/**
 * Non-Application entry point for the JAR.
 * JavaFX refuses to start from the classpath when the main class extends Application,
 * so this plain class delegates to MainApp.main() to bypass that check.
 */
public class Launcher {
    public static void main(String[] args) {
        MainApp.main(args);
    }
}
