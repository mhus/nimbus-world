#!/bin/bash

set -e

cd $(dirname "$0")
cd docker-ts
./build-all.sh "$@"
cd ..

cd docker-jvm
./build-all.sh "$@"
cd ..


