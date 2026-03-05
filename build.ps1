# Build and Run OpenRobotics JavaFX Application
# This script compiles and runs the application using Maven

$projectRoot = $PSScriptRoot
$projectPath = Join-Path $projectRoot "open-robotics"
$pomFile = Join-Path $projectPath "pom.xml"

Write-Host "╔══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║         OpenRobotics - Warehouse Simulation Platform         ║" -ForegroundColor Cyan
Write-Host "║                     JavaFX Build System                      ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# Check if Maven is available
$mvnPath = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnPath) {
    Write-Host "[ERROR] Maven not found in PATH" -ForegroundColor Red
    Write-Host "Please install Maven or add it to your PATH" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Alternatively, use Maven directly:" -ForegroundColor Green
    Write-Host "  cd open-robotics" -ForegroundColor Green
    Write-Host "  mvn clean compile javafx:run" -ForegroundColor Green
    exit 1
}

Write-Host "[INFO] Maven found at: $($mvnPath.Source)" -ForegroundColor Green
Write-Host "[INFO] Building project..." -ForegroundColor Cyan
Write-Host ""

# Change to project directory
Push-Location $projectPath

# Clean and compile
Write-Host "[STEP 1] Cleaning build artifacts..." -ForegroundColor Yellow
& mvn clean -q

Write-Host "[STEP 2] Compiling Java source files..." -ForegroundColor Yellow
& mvn compile -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed!" -ForegroundColor Red
    Pop-Location
    exit 1
}

Write-Host "[STEP 3] Running JavaFX application..." -ForegroundColor Yellow
Write-Host ""

# Run the application
& mvn javafx:run

Pop-Location

Write-Host ""
Write-Host "Build complete!" -ForegroundColor Green

