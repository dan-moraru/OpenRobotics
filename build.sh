#!/bin/bash
# Build and run OpenRobotics JavaFX application

if ! cd "$(dirname "$0")/open-robotics"; then
    echo "Error: open-robotics directory not found relative to script location" >&2
    exit 1
fi

# Try using mvn if available in PATH
if command -v mvn &> /dev/null; then
    mvn clean package -DskipTests || exit $?
    mvn javafx:run
else
    echo "Maven not found in PATH"
    echo "Please install Maven or run: mvn clean package -DskipTests && mvn javafx:run"
    exit 1
fi

