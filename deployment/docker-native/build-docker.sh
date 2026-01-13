#!/bin/bash
set -e

cd $(dirname "$0")
cd ../..

# export BP_PLATFORM_VARIANTS="linux/amd64,linux/arm64"

mvn clean install -DskipTests

cd world-player
mvn -Pnative spring-boot:build-image -DskipTests
cd ..

cd world-control
mvn -Pnative spring-boot:build-image -DskipTests
cd ..

cd world-life
mvn -Pnative spring-boot:build-image -DskipTests
cd ..


