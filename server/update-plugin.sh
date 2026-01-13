#!/bin/bash
set -e

echo "Installing plugin..."
cd /Users/hummel/sources/mhus/nimbus-server/server/plugins/generate-ts-to-java-maven-plugin
mvn clean install -q

echo "Regenerating generated module..."
cd /Users/hummel/sources/mhus/nimbus-server/server/generated
mvn clean generate-sources 2>&1 | tee /tmp/maven-output.log

echo "Checking for TsParser debug output..."
grep -i "TsParser" /tmp/maven-output.log || echo "No TsParser debug found"

echo "Done! Check StepWait.java"
grep "seconds" src/main/java/de/mhus/nimbus/generated/scrawl/StepWait.java
