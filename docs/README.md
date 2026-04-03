# 📚 OpenRobotics Documentation

Welcome to the OpenRobotics documentation hub. This folder contains all guides, architecture documentation, and API references for the warehouse simulation platform.

## 📖 Quick Navigation

### Getting Started
- **[Quick Start Guide](./guides/QUICKSTART.md)** - Get running in 5 minutes ⚡
- **[Setup Guide](./guides/SETUP_GUIDE.md)** - Detailed installation and IDE configuration 🔧
- **[README](../README.md)** - Project overview and feature summary 🏠

### Architecture & Design
- **[UI Implementation](./UI_IMPLEMENTATION.md)** - Screen design, styling, and navigation 🎨
- **[Screen Design Wireframes](./images/)** - Visual mockups and color schemes 📐

### Development
- **Build Scripts** - Automated compilation and execution
  - `../build.ps1` - PowerShell build script (Windows)
  - `../build.sh` - Bash build script (Linux/Mac)
  - `../verify.ps1` - Project verification script

## 📂 Documentation Structure

```
docs/
├── README.md                          # This file
├── guides/                            # User and setup guides
│   ├── QUICKSTART.md                 # 5-minute quick start
│   └── SETUP_GUIDE.md                # Detailed setup instructions
├── architecture/                      # Architecture documentation (future)
├── api/                               # API documentation (future)
├── images/                            # Wireframes and mockups
│   ├── 1440p\ Desktop\ -\ SETUP\ SCREEN.png
│   ├── 1440p\ Desktop\ -\ SIMULATION\ SCREEN.png
│   ├── 1440p\ Desktop\ -\ RESULTS.png
│   ├── 1440p\ Desktop\ -\ WELCOME\ SCREEN.png
│   ├── Specialty\ Screens.png
│   ├── Logo@4xBLACK\ 1.png
│   └── Logo@4xcolor\ 3.png
└── UI_IMPLEMENTATION.md               # UI design and implementation details
```

## 🚀 Getting Started

### For New Users
1. Start with **[QUICKSTART.md](./guides/QUICKSTART.md)** for immediate setup
2. Run the verification script: `.\verify.ps1`
3. Build and run: `.\build.ps1`

### For Developers
1. Read **[SETUP_GUIDE.md](./guides/SETUP_GUIDE.md)** for detailed configuration
2. Review **[UI_IMPLEMENTATION.md](./UI_IMPLEMENTATION.md)** for architecture
3. Explore the codebase in `open-robotics/src/`

### For Contributors
1. Ensure all files from the guides pass verification
2. Follow the architecture patterns in `UI_IMPLEMENTATION.md`
3. Maintain the color scheme and styling guidelines from the UI documentation
4. Add new documentation to the appropriate folder

## 🎯 Key Topics

### Installation & Setup
- Java 21+ installation
- Maven 3.8+ setup
- IDE configuration (IntelliJ IDEA / VS Code)
- Scene Builder integration

### Building & Running
- Maven commands for compilation
- PowerShell/Bash build scripts
- Running the application
- Troubleshooting common issues

### UI Development
- FXML screen files
- CSS styling framework
- Screen navigation system
- Dialog management

### Project Structure
- Directory organization
- Maven configuration
- JavaFX integration
- Resource management

## 🔧 Useful Commands

### Quick Build
```bash
cd open-robotics
mvn clean compile javafx:run
```

### Verification
```powershell
.\verify.ps1
```

### Full Build & Run
```powershell
.\build.ps1
```

## 📋 Checklist for Development

Before starting:
- [ ] Java 21+ installed
- [ ] Maven 3.8+ installed
- [ ] Verification passes: `.\verify.ps1`
- [ ] Application runs: `mvn javafx:run`
- [ ] IDE configured
- [ ] Scene Builder installed (optional)

## 🎨 Design Resources

### Color Palette
All UI colors are documented in:
- [QUICKSTART.md - Color Reference](./guides/QUICKSTART.md#-color-reference)
- [UI_IMPLEMENTATION.md - Color Scheme](./UI_IMPLEMENTATION.md#-ui-color-scheme-from-wireframe-analysis)

### Wireframes
Visual mockups available in:
- `images/` folder - High-resolution screenshots
- [SETUP_GUIDE.md](./guides/SETUP_GUIDE.md) - Screen descriptions

### Logo Files
- `../src/main/resources/com/openrobotics/img/logo_color.png` - Color version
- `../src/main/resources/com/openrobotics/img/logo_black.png` - Monochrome version

## 📞 Support

### Having Issues?
1. Check [QUICKSTART.md - Troubleshooting](./guides/QUICKSTART.md#-common-issues--fixes)
2. Run verification: `.\verify.ps1`
3. Review relevant section in [SETUP_GUIDE.md](./guides/SETUP_GUIDE.md)

### Need Help with Specific Topics?
- **Build issues** → See SETUP_GUIDE.md build section
- **UI editing** → See QUICKSTART.md editing section
- **Architecture** → See UI_IMPLEMENTATION.md
- **Navigation** → See QUICKSTART.md navigation flow

## 📚 External References

- [JavaFX Official Docs](https://openjfx.io/)
- [Scene Builder Guide](https://gluonhq.com/products/scene-builder/)
- [Maven Documentation](https://maven.apache.org/)
- [Java 21 Release Notes](https://openjdk.org/projects/jdk/21/)

## 📝 Document Overview

| Document | Purpose | Audience |
|----------|---------|----------|
| QUICKSTART.md | Fast setup in 5 minutes | New users, testers |
| SETUP_GUIDE.md | Detailed configuration | Developers, DevOps |
| UI_IMPLEMENTATION.md | Design & architecture | Frontend developers |
| README.md | Project overview | Everyone |

## 🔄 Contributing to Documentation

When adding new documentation:
1. Place guides in `docs/guides/`
2. Place architecture docs in `docs/architecture/`
3. Place API docs in `docs/api/`
4. Update this index file with new documents
5. Keep Markdown formatting consistent
6. Include examples and code snippets where helpful

## ✅ Documentation Status

- ✅ Quick Start Guide (QUICKSTART.md)
- ✅ Setup Guide (SETUP_GUIDE.md)
- ✅ UI Implementation Summary (UI_IMPLEMENTATION.md)
- ✅ Main README (README.md)
- ✅ Build Scripts Documentation
- ⏳ API Documentation (planned)
- ⏳ Architecture Diagrams (planned)
- ⏳ Development Guidelines (planned)

## 📅 Last Updated

March 5, 2026 - v0.3.0 (Skeleton Release)

## 📄 License

Documentation is part of the OpenRobotics Initiative - Warehouse Simulation Platform

---

**Happy coding! 🚀**  
Start with [QUICKSTART.md](./guides/QUICKSTART.md) for immediate setup.

