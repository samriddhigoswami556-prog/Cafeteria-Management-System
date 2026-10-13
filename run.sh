#!/usr/bin/env bash
# Linux / macOS:  ./run.sh setup   (create tables + sample data)   |   ./run.sh   (start website)
set -e
JAR=$(ls lib/ojdbc*.jar 2>/dev/null | head -1 || true)
[ -z "$JAR" ] && { echo "Put ojdbc11.jar in the lib/ folder first (see README step 3)"; exit 1; }
mkdir -p out && javac -encoding UTF-8 -cp "$JAR" -d out src/*.java
if [ "$1" = "setup" ]; then java -cp "out:$JAR" SetupDb; else java -cp "out:$JAR" Main; fi
