# OpenRobotics

<p align="center">
  <img src="docs/images/Logo@4xcolor%203.png" alt="OpenRobotics logo" width="220">
</p>

<p align="center"><strong>Multi-Robot Warehouse Simulation Platform</strong></p>

<p align="center">
  <a href="https://github.com/dan-moraru/OpenRobotics/actions/workflows/ci.yml"><img src="https://github.com/dan-moraru/OpenRobotics/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"><img src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white" alt="Java 21"></a>
  <a href="https://openjfx.io/"><img src="https://img.shields.io/badge/JavaFX-21-0A88FF" alt="JavaFX 21"></a>
  <a href="https://maven.apache.org/"><img src="https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white" alt="Maven"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License"></a>
</p>

OpenRobotics is a JavaFX-based warehouse simulation platform where multiple autonomous robots navigate a configurable warehouse environment to complete pickup and delivery tasks. The simulation supports multiple navigation algorithms, coordination policies, real-time 2D visualization, a heatmap overlay, and a results screen with per-robot statistics that can also be logged to a PostgreSQL database.

At its core, the project is aimed at studying how warehouse workloads can be completed efficiently and reliably using teams of robots, and what trade-offs emerge among fleet size, throughput, task distribution, and workload completion time under different navigation and coordination designs.

## Table of Contents

- [Features](#features)
- [Built With](#built-with)
- [Navigation and Coordination](#navigation-and-coordination)
- [Architecture Overview](#architecture-overview)
- [Quick Start](#quick-start)
- [Development Commands](#development-commands)
- [Testing and CI](#testing-and-ci)
- [Documentation](#documentation)
- [Project Structure](#project-structure)
- [Authors](#authors)
- [License](#license)

## Features

- Config file loading and saving in JSON format.
- Template maps and random map generation.
- Drag-and-drop entity placement in the simulation editor.
- Navigation algorithms: Greedy, Bug, and RTA*.
- Coordination policies: None, Traffic Rules, and Reservation-K.
- Proximity and range sensors per robot.
- Deadlock detection and recovery.
- Heatmap visualization.
- Per-robot statistics, including distance, energy, tasks completed, and idle ticks.
- Results export and optional PostgreSQL logging.

## Built With

| Category | Technology | Version | Notes |
| --- | --- | --- | --- |
| Language | Java | 21 | Primary application language |
| UI | JavaFX | 21 | Desktop UI framework |
| Build | Maven | N/A | Build, test, and packaging |
| Database | PostgreSQL | N/A | Optional run logging |
| DB Migrations | Flyway | 10.17.0 | Schema migration management |
| Connection Pooling | HikariCP | 5.1.0 | Shared JDBC connection pool |
| JSON | Jackson Databind | 2.16.1 | Config and export serialization |
| Logging Backend | Logback Classic | 1.5.16 | Logging implementation |
| Unit Testing | JUnit Jupiter | 5.10.1 | Test framework |
| UI Testing | TestFX | 4.0.18 | JavaFX UI tests |
| Coverage | JaCoCo | 0.8.12 | Coverage reporting |
| Mutation Testing | PIT | 1.22.0 | Mutation testing profile |

## Navigation and Coordination

### Navigation Algorithms

- **Greedy**: moves toward the target using Manhattan distance, with deterministic tie-breaking, sensor-aware preference, and backtracking when necessary.
- **Bug**: uses a Bug2-style approach that alternates between direct goal-seeking and left-hand boundary following around obstacles.
- **RTA\***: uses Real-Time A* with a per-robot learned heuristic table while keeping transient sensor penalties separate from learned costs.

### Coordination Policies

- **None**: robots follow their move intentions without additional coordination filtering.
- **Traffic Rules**: marked intersection tiles are treated as exclusive regions, with entry prioritized by wait time and load status.
- **Reservation-K**: a robot must reserve the next `k` tiles on its path before moving, reducing head-on and path-overlap conflicts.

## Architecture Overview

The codebase is organized into a few clear subsystems:

- **Application and UI**: JavaFX screens, controllers, styling, and navigation utilities.
- **Simulation Core**: engine tick loop, dispatcher, coordination policies, and collision handling.
- **Robot Domain**: robot state, algorithms, and sensor models.
- **Map Domain**: tiles, vectors, map entities, and environment objects.
- **Tasking**: task generation, lifecycle, and assignment support.
- **Persistence and Logging**: database access, Flyway migrations, DAO layer, and run/event logging.
- **Configuration and I/O**: JSON config loading, saving, and export DTOs.

## Quick Start

### Prerequisites

- Java 21
- Maven

### Clone and Run

1. Clone the repository:

   ```bash
   git clone https://github.com/dan-moraru/OpenRobotics.git
   cd OpenRobotics/open-robotics
   ```

2. Start the application:

   ```bash
   mvn clean javafx:run
   ```

From the repository root, you can alternatively use the helper script:

```bash
./build.sh
```

### Optional Database Setup

Database-backed logging is optional. If no database configuration is available, the application still starts, but remote logging features are unavailable.

For local development, copy the provided example file:

```bash
cp src/main/resources/application.config.example src/main/resources/application.config
```

Then edit `src/main/resources/application.config` with your credentials:

```properties
db.url=jdbc:postgresql://<host>:5432/postgres?sslmode=require
db.user=<user>
db.password=<password>
```

Environment variables `DB_URL`, `DB_USER`, and `DB_PASSWORD` are also supported and take precedence over file values.

### Sample Configurations

The repository includes several sample JSON configurations under `configs/` inside the `open-robotics` module. Examples include:

- `warehouse-config.json`
- `warehouse_2.json`
- `test_scenario.json`

### Standalone Packaging

To build the standalone packaged JAR:

```bash
mvn clean package -Pfat-jar -DskipTests
```

This packaging profile bundles JavaFX dependencies for Windows, macOS, and Linux. The standalone shaded artifact is generated under:

```text
target/open-robotics-<version>-standalone.jar
```

## Development Commands

Run these commands from the `open-robotics` directory:

- Run the app: `mvn clean javafx:run`
- Compile only: `mvn compile`
- Run all tests: `mvn test`
- Run verification and coverage: `mvn clean verify`
- Run mutation tests: `mvn test -Pmutation`
- Build packaged artifacts: `mvn package -DskipTests`
- Build the standalone packaged JAR: `mvn package -Pfat-jar -DskipTests`
- Clean old artifacts: `mvn clean`
- Show dependencies: `mvn dependency:tree`

## Testing and CI

The project currently includes **64 test classes** under `src/test/java`, covering hundreds of unit, UI, and integration test cases.

- **Unit and UI tests** run through Maven and include TestFX-based controller and UI coverage.
- **Coverage reporting** is generated via JaCoCo during `mvn clean verify`.
- **Mutation testing** is available through the `mutation` Maven profile.

The repository also includes a GitHub Actions CI workflow that:

- Runs on pushes and pull requests targeting `main` and `dev`.
- Uses Temurin JDK 21.
- Executes `mvn clean verify` from the `open-robotics` module.
- Uploads Surefire and JaCoCo artifacts.

## Documentation

Additional project documents are available in [`docs/`](docs):

- [Project Pitch](docs/Project%20Pitch%20-%20OpenRobotics.pdf)
- [Specification Document](docs/Specification%20Document%20-%20OpenRobotics.pdf)
- [Design Document](docs/Design%20Document%20-%20OpenRobotics.pdf)

## Project Structure

```text
open-robotics/
├── src/main/java/com/openrobotics/
│   ├── MainApp.java                  # JavaFX application entry point
│   ├── Launcher.java                 # Plain main class for packaged JAR launch
│   ├── controllers/                  # FXML screen controllers
│   ├── db/                           # DAO layer, models, record builders, database access
│   ├── io/                           # Config file loading, saving, and export DTOs
│   ├── logging/                      # Logger and event types
│   ├── map/                          # Map, tile, vector, and entity types
│   ├── robot/                        # Robot model, navigation, and sensors
│   ├── simulationcore/               # Engine, dispatcher, policies, and collision handling
│   ├── task/                         # Task generation and task lifecycle
│   └── util/                         # Screen navigation, icon loading, viewport tips, and helpers
├── src/main/resources/
│   ├── application.config.example    # Example local database config
│   ├── com/openrobotics/fxml/        # FXML screen and dialog layouts
│   ├── com/openrobotics/css/         # Global stylesheet
│   ├── com/openrobotics/img/         # Logo and simulation assets
│   └── db/migration/                 # Flyway SQL migrations
├── src/test/java/com/openrobotics/   # Unit, integration, and UI tests
├── configs/                          # Sample JSON simulation configs
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

## License

Distributed under the MIT License. See [`LICENSE`](LICENSE) for details.