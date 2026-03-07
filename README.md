# OpenRobotics - Warehouse Simulation Platform

A modern JavaFX-based multi-robot warehouse simulation platform with real-time visualization and comprehensive results analysis.

## Quick Links

- **[QUICKSTART.md](./QUICKSTART.md)** - Get running in 5 minutes
- **[SETUP_GUIDE.md](./SETUP_GUIDE.md)** - Detailed setup and development guide  
- **[UI_IMPLEMENTATION_SUMMARY.md](./UI_IMPLEMENTATION_SUMMARY.md)** - UI architecture overview

## ✨ Features

### User Interface (Sprint 3 - Complete)
✅ **4 Main Screens**: Welcome, Setup, Simulation, Results  
✅ **4 Dialog Windows**: Exit confirmation, Load/Save config, Object descriptions  
✅ **Professional Styling**: Warm, neutral color scheme (#C2BEAE, #8D8A7F, #5D5B54)  
✅ **Logo Integration**: OpenRobotics branding on all screens  
✅ **Responsive Layout**: Adapts to window resizing  
✅ **Navigation System**: Seamless screen transitions via ScreenNavigator  
✅ **CSS Framework**: 800+ lines of organized styling  

### Core Functionality
- Configuration management (load/save configs)
- Real-time 2D simulation visualization
- Object placement and properties editing
- Playback controls (play, pause, step, restart, speed)
- Results analysis with charts and statistics

## 🚀 Quick Start

### Prerequisites
- Java 21 or later (`java -version`)
- Maven 3.8 or later (`mvn -version`)
- (Optional) JavaFX Scene Builder for visual editing

### Installation & Run (Windows)
```powershell
cd C:\Users\Admin\IdeaProjects\OpenRobotics

# Option 1: Using verification script (recommended)
.\verify.ps1      # Check everything is set up
.\build.ps1       # Build and run

# Option 2: Using Maven directly
cd open-robotics
mvn clean compile javafx:run

# Option 3: Using Maven from the repo root
mvn -f open-robotics/pom.xml clean compile javafx:run
```

### Installation & Run (Mac/Linux)
```bash
cd ~/IdeaProjects/OpenRobotics/open-robotics
mvn clean compile javafx:run
```

## 📋 Project Structure

```
open-robotics/
├── src/main/
│   ├── java/com/openrobotics/
│   │   ├── MainApp.java                    # Entry point
│   │   ├── Robot.java                      # Simulation logic
│   │   ├── controllers/                    # 8 FXML controllers
│   │   ├── model/                          # Data models
│   │   └── util/ScreenNavigator.java       # Navigation system
│   └── resources/com/openrobotics/
│       ├── fxml/                           # 8 FXML screen files
│       ├── css/theme.css                   # Main stylesheet (800+ lines)
│       └── img/                            # Logo images
├── pom.xml                                 # Maven configuration
└── src/test/java/                          # Unit tests
```

## 🎨 UI Color Scheme

| Hex | RGB | Usage |
|-----|-----|-------|
| `#C2BEAE` | (194,190,174) | Primary light backgrounds |
| `#CDCBC3` | (205,203,195) | Viewports & secondary backgrounds |
| `#8D8A7F` | (141,138,127) | Tabs, sidebars, accents |
| `#5D5B54` | (93,91,84) | Dark panels, object tiles |
| `#32312D` | (50,49,45) | Console background, dark buttons |
| `#4D4B45` | (77,75,69) | Simulation/Results header |
| `#3D3C39` | (61,60,57) | Setup screen header |
| `#599068` | (89,144,104) | Success/confirm buttons |
| `#AA8478` | (170,132,120) | Danger/delete buttons |

## 📚 Documentation

### For First-Time Setup
See [QUICKSTART.md](./QUICKSTART.md) for a 5-minute getting started guide.

### For Development
See [SETUP_GUIDE.md](./SETUP_GUIDE.md) for detailed setup, IDE configuration, and development instructions.

### For Architecture Details
See [UI_IMPLEMENTATION_SUMMARY.md](./UI_IMPLEMENTATION_SUMMARY.md) for CSS classes, navigation flow, and technical decisions.

## 💻 Build & Run Commands

Make sure you're in the `open-robotics` directory:

```bash
# Development
mvn clean compile javafx:run          # Run application
mvn test                              # Run all tests
mvn compile                           # Compile only
mvn clean                             # Clean build artifacts

# Distribution
mvn package                           # Build JAR
java -jar target/open-robotics-1.0.0.jar  # Run JAR
```

You can also use the `IDE's integrated Maven tool window` to run these commands.

## 🎯 Screen Navigation

```
Welcome Screen (changelog)
    ↓ click
Setup Screen (configuration)
    ├─→ Load/Save Dialogs
    ├─→ Exit Confirm Dialog
    └─→ Simulation Screen (START button)
        ├─→ Object Description Dialog
        ├─→ Results Screen
        └─→ Exit Confirm Dialog
```

## 🔌 IDE Setup

### IntelliJ IDEA
1. File → Open → Select `C:\Users\Admin\IdeaProjects\OpenRobotics`
2. Right-click `MainApp.java` → Run
3. (Optional) Settings → JavaFX → Set Scene Builder path for visual editing

### VS Code
1. Open folder: `C:\Users\Admin\IdeaProjects\OpenRobotics`
2. Install "Extension Pack for Java"
3. Open terminal and run: `cd open-robotics && mvn javafx:run`

## 🛠️ Editing UI with Scene Builder

1. Right-click any `.fxml` file in IDE
2. Select "Open in Scene Builder"
3. Drag-drop components visually
4. Changes auto-sync to XML when you save

## ✅ Verification Checklist

- [ ] Java 21+ installed (`java -version`)
- [ ] Maven installed (`mvn -version`)
- [ ] Run `.\verify.ps1` - all checks pass
- [ ] Application starts (`mvn javafx:run`)
- [ ] All 4 screens load correctly
- [ ] Logo displays on screens
- [ ] Colors match wireframe design

## 📝 Dev Usage

The following tools make development easier:

## Authors
- Dan Moraru, 261227203
- Badr Zejli, 261239011
- Filip Snítil, 261139844
- Lounes Azzoun, 261181741
- Daniel Blackburn, 261112665
- Muhammad Sohail, 261142698
- Murad Novruzov, 261164063
- Behnam Yosufi, 261125449