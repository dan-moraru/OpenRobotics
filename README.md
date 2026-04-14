# OpenRobotics
Multi-Robot Warehouse Simulation Platform

Check the repo's Project tab for the Kanban board.

## Description

OpenRobotics is a JavaFX-based warehouse simulation platform where multiple autonomous robots navigate a configurable warehouse environment to complete pickup and delivery tasks. The simulation supports multiple navigation algorithms (Greedy, Bug, RTA*), coordination policies (Traffic Rules, Reservation-K), real-time 2D visualization, a heatmap overlay, and a results screen with per-robot statistics logged to a remote PostgreSQL database.

## Features

- 4 screens: Welcome, Setup, Simulation Editor, Results
- Config file loading and saving (JSON)
- Template maps and random map generation
- Drag-and-drop entity placement in the editor
- Navigation algorithms: Greedy, Bug, RTA*
- Coordination policies: None, Traffic Rules, Reservation-K
- Proximity and range sensors per robot
- Deadlock detection and recovery
- Heatmap visualization
- Per-robot statistics: distance, energy, tasks completed, idle ticks
- Results export and database logging (PostgreSQL via Supabase)

## Dev Install

1. Make sure you have `Java SDK 21` or higher
2. Clone the [repo](https://github.com/dan-moraru/OpenRobotics)
3. Set up the database config file (see Database Setup below)
4. Open a terminal and run `cd open-robotics`
5. Run `mvn clean javafx:run` or use the IDE's integrated Maven tool window

### Database Setup

The app connects to a remote PostgreSQL database hosted on Supabase for logging simulation results. Create the file `open-robotics/application.config` with the following content (fill in your credentials):

```
DB_URL=jdbc:postgresql://<host>:5432/postgres?sslmode=require
DB_USER=<user>
DB_PASSWORD=<password>
```

This file is gitignored and must be created locally. Without it, the app will still run but database logging will be unavailable.

## Dev Usage

Make sure you are in the `open-robotics` directory before running these commands:

- Run program: `mvn clean javafx:run`
- Run all tests: `mvn test`
- Run mutation tests: `mvn test -Pmutation`
- Build program to JAR: `mvn package -DskipTests`
- Compile only: `mvn compile`
- Clean old artifacts: `mvn clean`
- Show dependencies: `mvn dependency:tree`

You can also run any of these through the IDE's integrated Maven tool window.

## Project Structure

```
open-robotics/
├── src/main/java/com/openrobotics/
│   ├── MainApp.java                  # Entry point
│   ├── controllers/                  # FXML screen controllers
│   ├── db/                           # DAO layer, models, record builders
│   ├── io/                           # Config file loading and saving
│   ├── logging/                      # Logger and event types
│   ├── map/                          # Map, Tile, Vector2D, entities
│   ├── robot/                        # Robot, navigation, sensors
│   ├── simulationcore/               # Engine, dispatcher, policies
│   ├── task/                         # Task, TaskGenerator, TaskStatus
│   └── util/                         # ScreenNavigator, IconLoader, etc.
├── src/main/resources/com/openrobotics/
│   ├── css/theme.css                 # Global stylesheet
│   ├── fxml/                         # Screen and dialog FXML files
│   └── img/                          # Logo and entity icons
├── src/main/resources/db/migration/  # Flyway SQL migration scripts
└── pom.xml
```

## Authors

- Dan Moraru, 261227203
- Badr Zejli, 261239011
- Filip Snítil, 261139844
- Lounes Azzoun, 261181741
- Daniel Blackburn, 261112665
- Muhammad Sohail, 261142698
- Murad Novruzov, 261164063
- Behnam Yosufi, 261125449
