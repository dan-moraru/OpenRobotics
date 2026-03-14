#!/usr/bin/env pwsh
# ═══════════════════════════════════════════════════════════════════════════════
# OpenRobotics UI Verification & Compilation Script
# ═══════════════════════════════════════════════════════════════════════════════
# This script validates the project setup and attempts compilation

param(
    [switch]$SkipCompile,
    [switch]$Run,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"
$script:errors = 0
$script:warnings = 0

# ─── HELPER FUNCTIONS ────────────────────────────────────────────────────────

function Write-Section {
    param([string]$Title)
    $trimmedTitle = if ($Title.Length -gt 62) { $Title.Substring(0, 62) } else { $Title }
    Write-Host ""
    Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║ $($trimmedTitle.PadRight(62)) ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Message)
    Write-Host "  ✓ $Message" -ForegroundColor Green
}

function Write-Error-Custom {
    param([string]$Message)
    Write-Host "  ✗ $Message" -ForegroundColor Red
    $script:errors++
}

function Write-Warning-Custom {
    param([string]$Message)
    Write-Host "  ⚠ $Message" -ForegroundColor Yellow
    $script:warnings++
}

function Test-FileExists {
    param([string]$Path, [string]$Description)
    if (Test-Path $Path) {
        Write-Success "$Description"
        return $true
    } else {
        Write-Error-Custom "$Description not found at: $Path"
        return $false
    }
}

# ─── MAIN VERIFICATION ──────────────────────────────────────────────────────

Write-Host ""
Write-Host "╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Magenta
Write-Host "║     OpenRobotics JavaFX UI - Verification & Build Script      ║" -ForegroundColor Magenta
Write-Host "║                     March 5, 2026 - v0.3.0                    ║" -ForegroundColor Magenta
Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Magenta

$projectRoot = Split-Path -Parent $PSScriptRoot
$mavenProject = Join-Path $projectRoot "open-robotics"

Write-Section "1. Environment Validation"

# Check Java
Write-Host "Checking Java installation..."
$javaVersion = java -version 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "Java is installed"
    if ($Verbose) { Write-Host "    $($javaVersion[2])" -ForegroundColor Gray }
} else {
    Write-Error-Custom "Java not found in PATH"
}

# Check Maven
Write-Host "Checking Maven installation..."
$mvnVersion = mvn -version 2>&1
if ($LASTEXITCODE -eq 0) {
    Write-Success "Maven is installed"
    if ($Verbose) { Write-Host "    $($mvnVersion[0])" -ForegroundColor Gray }
} else {
    Write-Error-Custom "Maven not found in PATH"
    Write-Host "  Install Maven: choco install maven" -ForegroundColor Yellow
}

Write-Section "2. Project Structure Validation"

# Core FXML files
Write-Host "Checking FXML screen files..."
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/WelcomeScreen.fxml" "WelcomeScreen.fxml"
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/SetupScreen.fxml" "SetupScreen.fxml"
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/SimulationScreen.fxml" "SimulationScreen.fxml"
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/ResultsScreen.fxml" "ResultsScreen.fxml"

# Dialog FXML files
Write-Host "Checking FXML dialog files..."
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/ExitConfirmDialog.fxml" "ExitConfirmDialog.fxml"
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/LoadConfigDialog.fxml" "LoadConfigDialog.fxml"
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/SaveConfigDialog.fxml" "SaveConfigDialog.fxml"
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/fxml/ObjectDescDialog.fxml" "ObjectDescDialog.fxml"

# CSS Theme
Write-Host "Checking CSS theme file..."
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/css/theme.css" "theme.css (Main stylesheet)"

# Logo images
Write-Host "Checking logo images..."
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/img/logo_color.png" "logo_color.png"
Test-FileExists "$mavenProject/src/main/resources/com/openrobotics/img/logo_black.png" "logo_black.png"

# Controller classes
Write-Host "Checking controller classes..."
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/WelcomeController.java" "WelcomeController.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/SetupController.java" "SetupController.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/SimulationController.java" "SimulationController.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/ResultsController.java" "ResultsController.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/ExitConfirmController.java" "ExitConfirmController.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/LoadConfigController.java" "LoadConfigController.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/SaveConfigController.java" "SaveConfigController.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/controllers/ObjectDescController.java" "ObjectDescController.java"

# Main app files
Write-Host "Checking core application files..."
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/MainApp.java" "MainApp.java"
Test-FileExists "$mavenProject/src/main/java/com/openrobotics/util/ScreenNavigator.java" "ScreenNavigator.java"
Test-FileExists "$mavenProject/pom.xml" "pom.xml"

Write-Section "3. Build Configuration"

# Check Maven POM
Write-Host "Validating pom.xml..."
if (-not (Test-Path "$mavenProject/pom.xml")) {
    Write-Error-Custom "pom.xml not found at: $mavenProject/pom.xml"
    exit 1
}

$pomContent = Get-Content "$mavenProject/pom.xml" -Raw
if ($pomContent -match "javafx-maven-plugin") {
    Write-Success "JavaFX Maven plugin configured"
} else {
    Write-Warning-Custom "JavaFX Maven plugin not found in pom.xml"
}

if ($pomContent -match "release>21") {
    Write-Success "Java 21 target configured"
} else {
    Write-Warning-Custom "Java 21 not configured in pom.xml"
}

Write-Section "4. Summary"

Write-Host ""
Write-Host "Errors Found:   $script:errors" -ForegroundColor $(if ($script:errors -gt 0) { "Red" } else { "Green" })
Write-Host "Warnings Found: $script:warnings" -ForegroundColor $(if ($script:warnings -gt 0) { "Yellow" } else { "Green" })
Write-Host ""

if ($script:errors -gt 0) {
    Write-Host "❌ Verification FAILED - Please fix errors above" -ForegroundColor Red
    exit 1
}

if (-not $SkipCompile) {
    Write-Section "5. Building Project"

    Write-Host "Running Maven clean compile..." -ForegroundColor Cyan
    Push-Location $mavenProject

    try {
        mvn clean compile -q

        if ($LASTEXITCODE -eq 0) {
            Write-Success "Compilation successful!"

            if ($Run) {
                Write-Host ""
                Write-Section "6. Running Application"
                Write-Host "Starting JavaFX application..." -ForegroundColor Cyan
                mvn javafx:run
            }
        } else {
            Write-Error-Custom "Compilation failed with exit code $LASTEXITCODE"
            exit 1
        }
    } finally {
        Pop-Location
    }
} else {
    Write-Host "Compilation skipped (requested via -SkipCompile)" -ForegroundColor Gray
}

Write-Host ""
Write-Host "✅ OpenRobotics UI Verification Complete!" -ForegroundColor Green
Write-Host ""
Write-Host "Next Steps:" -ForegroundColor Cyan
Write-Host "  1. Open the project in IntelliJ IDEA"
Write-Host "  2. Configure Scene Builder in IDE settings (optional)"
Write-Host "  3. Run: mvn javafx:run  (to start the application)"
Write-Host "  4. Edit FXML files with: Right-click → Open in Scene Builder"
Write-Host ""

