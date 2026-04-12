# OpenRobotics UI Setup Guide

## Overview
This document explains how to set up the JavaFX visual builder (SceneBuilder) and build/run the OpenRobotics application.

## Prerequisites

### Required Software
- **Java 21+** (OpenJDK or Oracle JDK)
- **Maven 3.8+** (for building)
- **JavaFX Scene Builder** (optional, for editing FXML files visually)

### Installation Steps

#### 1. Install Java 21+
```bash
# Windows: Download from https://www.oracle.com/java/technologies/downloads/
# Or use Chocolatey:
choco install openjdk21

# macOS:
brew install openjdk@21

# Linux (APT example):
sudo apt update && sudo apt install openjdk-21-jdk
```

#### 2. Install Maven
```bash
# Windows (Chocolatey):
choco install maven

# Linux/Mac:
brew install maven
```

#### 3. Install Scene Builder (Optional but Recommended)
Download from: https://gluonhq.com/products/scene-builder/

### Configuration

#### IntelliJ IDEA Setup
1. **Open the Project**
   - File → Open → `<PROJECT_PATH>`
   - Select the `open-robotics` folder as the Maven project root

2. **Configure JavaFX Plugin in IntelliJ**
   - Go to: File → Settings → Languages & Frameworks → JavaFX
   - Set SceneBuilder Path: `C:\Program Files\SceneBuilder\SceneBuilder.exe` (or your installation path)

3. **Enable FXML Editing**
   - Right-click any `.fxml` file
   - Select "Open in SceneBuilder"

#### Scene Builder Configuration
To use Scene Builder with our project:
1. In Scene Builder, go to: Edit → Preferences → JavaFX
2. Set the CSS stylesheet path to: `src/main/resources/com/openrobotics/css/theme.css`
3. Add library JARs from Maven local repository (if needed)

## Building the Application

### Method 1: Using PowerShell Script (Windows)
```powershell
# Navigate to project root
cd <PROJECT_PATH>

# Run the build script
.\build.ps1
```

### Method 2: Using Maven Directly
```bash
cd <PROJECT_PATH>/open-robotics

# Compile only
mvn clean compile

# Compile and run
mvn clean compile javafx:run

# Build JAR
mvn clean package

# Run JAR (after building)
java -jar target/open-robotics-1.0.0.jar
```

### Method 3: Using IntelliJ IDEA
1. Right-click on `MainApp.java`
2. Select "Run 'MainApp.main()'"

## Project Structure

```
open-robotics/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/openrobotics/
│   │   │       ├── MainApp.java              # Application entry point
│   │   │       ├── Robot.java                # Core robot model
│   │   │       ├── controllers/              # FXML controller classes
│   │   │       ├── model/                    # Data models
│   │   │       └── util/
│   │   │           └── ScreenNavigator.java  # Screen navigation utility
│   │   └── resources/
│   │       └── com/openrobotics/
│   │           ├── fxml/                     # FXML scene files
│   │           │   ├── WelcomeScreen.fxml
│   │           │   ├── SetupScreen.fxml
│   │           │   ├── SimulationScreen.fxml
│   │           │   ├── ResultsScreen.fxml
│   │           │   └── *Dialog.fxml          # Dialog windows
│   │           ├── css/
│   │           │   └── theme.css             # Global theme styling
│   │           └── img/
│   │               ├── logo_color.png        # Logo (color)
│   │               └── logo_black.png        # Logo (monochrome)
│   └── test/
│       └── java/com/openrobotics/            # Unit tests
├── pom.xml                                    # Maven configuration
└── src/main/java/module-info.java             # Java module descriptor
```

## Screen Navigation

The application has 4 main screens that can be navigated using `ScreenNavigator`:

1. **Welcome Screen** (`WelcomeScreen.fxml`)
   - Shows version info and changelog
   - Click anywhere to proceed

2. **Setup Screen** (`SetupScreen.fxml`)
   - Configure simulation parameters
   - Load/Save configurations
   - Start simulation

3. **Simulation Screen** (`SimulationScreen.fxml`)
   - 2D viewport for visualization
   - Add/place objects
   - Control playback
   - View live statistics

4. **Results Screen** (`ResultsScreen.fxml`)
   - View simulation results
   - Charts and analytics
   - Object placement results

Additionally, there are dialog windows:
- `ExitConfirmDialog.fxml` - Confirm quit
- `LoadConfigDialog.fxml` - Load saved config
- `SaveConfigDialog.fxml` - Save current config
- `ObjectDescDialog.fxml` - Object information

## UI Styling

### Color Palette
The application uses a warm, neutral color scheme:

- **Primary Light**: `#C2BEAE` (Main background, light panels)
- **Primary Medium**: `#CDCBC3` (Secondary background, viewports)
- **Primary Medium-Dark**: `#8D8A7F` (Accents, tab strips)
- **Primary Dark**: `#5D5B54` (Dark panels, object tiles)
- **Primary Very Dark**: `#32312D` (Console background)
- **Header**: `#4D4B45` (Top bars on simulation/results screens)
- **Header Very Dark**: `#3D3C39` (Top bar on setup screen)

### CSS Classes
Key CSS classes used throughout:
- `.btn-primary`, `.btn-success`, `.btn-danger`, `.btn-neutral` - Button types
- `.tab-btn`, `.tab-btn-active` - Tab styling
- `.object-tile` - Object selection tiles
- `.dialog-*` - Dialog styling
- `.property-row` - Property row styling

See `src/main/resources/com/openrobotics/css/theme.css` for complete styling reference.

## Editing FXML Files

### Using Scene Builder (Recommended for Visual Editing)
1. Right-click on `.fxml` file in IntelliJ
2. Select "Open in SceneBuilder"
3. Drag-and-drop components to build UI
4. Bind components to controller methods
5. Save - changes sync to XML

### Using Text Editor (Direct XML Editing)
1. Edit `.fxml` files in IntelliJ's XML editor
2. Use IntelliJ's code completion for FXML elements
3. Refer to existing FXML files for structure examples

### Important FXML Notes
- Controller class must be specified: `fx:controller="..."`
- Component IDs must match `@FXML` variables in controller
- Event handler methods must match `onAction="#methodName"` in FXML
- CSS stylesheets must be referenced: `stylesheets="@../css/theme.css"`
- Logo images must be in `src/main/resources/com/openrobotics/img/`

## Common Issues & Solutions

### Issue: Maven command not found
**Solution**: Install Maven or add it to system PATH

### Issue: JavaFX modules not found
**Solution**: Java 21+ is required. For normal Maven builds (`mvn clean compile javafx:run`), JavaFX 21 is provided via Maven dependencies, so a separate manual JavaFX install is usually not needed. Manual/platform-specific JavaFX runtime setup may still be required for modular runtime images (`jlink`/`jpackage`) or native packaging workflows.

### Issue: Scene Builder won't open FXML
**Solution**: Configure Scene Builder path in IntelliJ settings

### Issue: CSS styling not applied
**Solution**: Ensure `stylesheets="@../css/theme.css"` is in root FXML element

### Issue: Controllers not loading
**Solution**: Check that controller class path matches `fx:controller` in FXML

## Next Steps

1. **Implement Controllers**: Add event handlers in controller classes
2. **Add Business Logic**: Implement simulation engine in `Robot.java`
3. **Database Integration**: Connect to PostgreSQL (if needed)
4. **Testing**: Run unit tests with `mvn test`
5. **Packaging**: Build distribution JAR with `mvn package`

## References

- [JavaFX Documentation](https://openjfx.io/)
- [Scene Builder Documentation](https://gluonhq.com/products/scene-builder/scene-builder-documentation/)
- [Maven Documentation](https://maven.apache.org/)
- [CSS Reference for JavaFX](https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/doc-files/cssref.html)

