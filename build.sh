#!/bin/bash
# Build and run OpenRobotics JavaFX application

cd "$(dirname "$0")/open-robotics"

# Try using mvn if available in PATH
if command -v mvn &> /dev/null; then
    mvn clean package -DskipTests
    mvn javafx:run
else
    echo "Maven not found in PATH"
    echo "Please install Maven or run: mvn clean compile javafx:run"
    exit 1
fi

