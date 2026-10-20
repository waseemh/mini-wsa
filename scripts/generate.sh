#!/usr/bin/env bash
# Generates security events, to a file or straight into a running API. Needs Java 21 and a built jar
# (mvn package -DskipTests). Examples:
#   scripts/generate.sh --count 10000 --out events.json     # then: curl -d @events.json ...
#   scripts/generate.sh --count 10000 --post http://localhost:8080
# All options: see GeneratorRunner.
set -euo pipefail
cd "$(dirname "$0")/.."
JAR=$(ls target/mini-wsa-*.jar 2>/dev/null | head -1)
[ -n "$JAR" ] || { echo "No jar found; run: mvn package -DskipTests" >&2; exit 1; }
exec java -Dloader.main=com.akamai.miniwsa.generator.GeneratorRunner -cp "$JAR" \
    org.springframework.boot.loader.launch.PropertiesLauncher "$@"
