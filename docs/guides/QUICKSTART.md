# OpenRobotics UI - Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Step 1: Verify Installation
```powershell
# Windows - Open PowerShell and run:
# Replace <PROJECT_PATH> with your local clone path:
cd <PROJECT_PATH>
.\verify.ps1
```

This will check that all required files are in place and Java/Maven are installed.

### Step 2: Build & Run
```powershell
# Option A: Using provided build script
.\build.ps1

# Option B: Using Maven directly
cd open-robotics
mvn clean compile javafx:run
```

### Step 3: Open in IDE
1. **IntelliJ IDEA**
   - File → Open → Select `<PROJECT_PATH>`
   - Select `<PROJECT_PATH>/open-robotics` as Maven project root
   - Right-click `MainApp.java` → Run

2. **Visual Studio Code**
   - Open folder: `<PROJECT_PATH>`
   - Install "Extension Pack for Java"
   - Run: `Ctrl+Shift+D` → Select Java

## 📁 Important Files

### To Edit UI Visually (with Scene Builder)
- Right-click any `.fxml` file in IDE
- Select "Open in Scene Builder"
- Drag-drop components to build UI
- Changes auto-sync to XML

### FXML Files (UI Screens)
```
src/main/resources/com/openrobotics/fxml/
├── WelcomeScreen.fxml          ← First screen (changelog)
├── SetupScreen.fxml             ← Configuration editor
├── SimulationScreen.fxml        ← Main simulation viewport
├── ResultsScreen.fxml           ← Results dashboard
├── ExitConfirmDialog.fxml       ← Quit confirmation
├── LoadConfigDialog.fxml        ← Load saved config
├── SaveConfigDialog.fxml        ← Save current config
└── ObjectDescDialog.fxml        ← Object info dialog
```

### Controller Classes (Logic)
```
src/main/java/com/openrobotics/controllers/
├── WelcomeController.java
├── SetupController.java
├── SimulationController.java
├── ResultsController.java
├── ExitConfirmController.java
├── LoadConfigController.java
├── SaveConfigController.java
└── ObjectDescController.java
```

### Styling
```
src/main/resources/com/openrobotics/css/
└── theme.css    ← All colors, fonts, button styles, etc.
```

### Images/Assets
```
src/main/resources/com/openrobotics/img/
├── logo_color.png   ← Color logo (used in screens)
└── logo_black.png   ← Black & white logo
```

<a id="-color-reference"></a>
## 🎨 Color Reference

All colors used in the UI:

| Name | Hex | Usage |
|------|-----|-------|
| Light Beige | `#C2BEAE` | Main backgrounds |
| Medium Beige | `#CDCBC3` | Viewports |
| Medium Gray | `#8D8A7F` | Tabs, sidebars |
| Dark Gray | `#5D5B54` | Dark panels |
| Very Dark | `#32312D` | Console |
| Header Dark | `#4D4B45` | Simulation header |
| Header Very Dark | `#3D3C39` | Setup header |
| Green | `#599068` | Confirm buttons |
| Red | `#AA8478` | Delete/Quit buttons |

## 🔄 Navigation Flow

```
START
  ↓
[Welcome Screen] - Shows changelog, click to continue
  ↓
[Setup Screen] - Configure simulation parameters
  ├─ LOAD → [Load Config Dialog]
  ├─ SAVE → [Save Config Dialog]
  ├─ File → Exit → [Exit Confirm Dialog]
  └─ START SIMULATION → 
      ↓
      [Simulation Screen] - Run simulation, add objects
        ├─ Add Object → [Object Desc Dialog]
        ├─ Calculate Results →
        │  ↓
        │  [Results Screen] - View results & charts
        │  └─ Editor Tab → Back to Setup
        └─ File → Exit → [Exit Confirm Dialog]
          ↓
        QUIT
```

## 💻 Command Reference

### Build Commands
```bash
# Clean build
mvn clean

# Compile only
mvn compile

# Compile + Run
mvn javafx:run

# Run tests
mvn test

# Build JAR
mvn package

# Run JAR (after mvn package)
java -jar target/open-robotics-1.0.0.jar
```

### PowerShell Scripts
```powershell
# Verify setup
.\verify.ps1

# Build & run
.\build.ps1

# Build & run with verbose output
.\build.ps1 -Verbose

# Verify without building
.\verify.ps1 -SkipCompile

# Verify and run
.\verify.ps1 -Run
```

## 🛠️ Editing FXML with Scene Builder

1. **Open File**
   - Right-click `.fxml` file in IntelliJ
   - Select "Open in Scene Builder"

2. **Add Components**
   - Drag from Library panel (left)
   - Drop into canvas area (center)

3. **Set Properties**
   - Right panel: Inspector
   - Set fx:id, text, onAction, etc.

4. **Wire to Controller**
   - Set `fx:controller` in root
   - Bind button actions: `onAction="#methodName"`
   - Bind component IDs: `fx:id="componentName"`

5. **Save**
   - File → Save (auto-updates XML)
   - Changes appear in FXML file immediately

## 📝 Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| Ctrl+Shift+D | Run with debugger (VS Code) |
| Shift+F10 | Run (IntelliJ) |
| Ctrl+Alt+L | Format code (IntelliJ) |
| Ctrl+/ | Toggle comment (both IDEs) |

<a id="-common-issues--fixes"></a>
## 🐛 Common Issues & Fixes

### "Java not found"
```powershell
# Install Java 21
choco install openjdk21

# Or download from:
# https://www.oracle.com/java/technologies/downloads/
```

### "Maven not found"
```powershell
# Install Maven
choco install maven

# Verify installation
mvn -version
```

### "FXML Controller not found"
- Check `fx:controller="com.openrobotics.controllers.WelcomeController"` exists in FXML
- Verify controller class is in correct package
- Rebuild project: `mvn clean compile`

### "CSS stylesheet not found"
- Ensure FXML has: `stylesheets="@../css/theme.css"`
- Check file exists at: `src/main/resources/com/openrobotics/css/theme.css`
- Rebuild project

### "Logo images not showing"
- Verify files exist:
  - `src/main/resources/com/openrobotics/img/logo_color.png`
  - `src/main/resources/com/openrobotics/img/logo_black.png`
- Check FXML image path: `<Image url="@../img/logo_color.png"/>`

## 📚 Learning Resources

- [JavaFX Official Docs](https://openjfx.io/)
- [Scene Builder Tutorial](https://gluonhq.com/products/scene-builder/)
- [FXML Guide](https://openjfx.io/javadoc/21/javafx.fxml/javafx/fxml/doc-files/introduction_to_fxml.html)
- [CSS Reference](https://openjfx.io/javadoc/21/javafx.graphics/javafx/scene/doc-files/cssref.html)

## ✅ Checklist

Before starting development:
- [ ] Java 21+ installed (`java -version`)
- [ ] Maven installed (`mvn -version`)
- [ ] Project verified (`.\verify.ps1`)
- [ ] Application runs (`mvn javafx:run`)
- [ ] IDE configured (IntelliJ or VS Code)
- [ ] Scene Builder installed (optional but recommended)

## 📞 Support

For issues:
1. Run `.\verify.ps1` to check setup
2. Check console output for error messages
3. Verify all files exist in correct locations
4. Try `mvn clean compile` to rebuild
5. Check that controller methods match FXML actions

---

**Version**: 0.3.0 (Skeleton)  
**Updated**: March 5, 2026  
**Status**: ✅ Ready to use

