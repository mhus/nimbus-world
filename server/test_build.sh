#!/bin/bash
cd /Users/hummel/sources/mhus/nimbus-server/server

echo "=== Testing shared module ==="
mvn -pl shared clean compile > shared_build.log 2>&1
if [ $? -eq 0 ]; then
    echo "shared: OK"
else
    echo "shared: FAILED"
    tail -20 shared_build.log
fi

echo "=== Testing world-shared module ==="
mvn -pl world-shared clean compile > world_shared_build.log 2>&1
if [ $? -eq 0 ]; then
    echo "world-shared: OK"
else
    echo "world-shared: FAILED"
    tail -20 world_shared_build.log
fi

echo "=== Testing full build ==="
mvn clean compile > full_build.log 2>&1
if [ $? -eq 0 ]; then
    echo "full build: OK"
else
    echo "full build: FAILED"
    tail -30 full_build.log
fi
