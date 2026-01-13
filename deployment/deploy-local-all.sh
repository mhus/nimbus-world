#!/bin/bash

set -e

git pull

cd $(dirname "$0")

if [ "$1" = "ts" ]; then
  cd docker-ts
  ./build-all.sh "${@:2}"
  cd ..
elif [ "$1" = "java" ]; then
  cd docker-jvm
  ./build-all.sh "${@:2}"
  cd ..
else
  cd docker-ts
  ./build-all.sh "$@"
  cd ..
  cd docker-jvm
  ./build-all.sh "$@"
  cd ..
fi

cd local-all
docker compose up -d
docker compose logs --tail 10 -f world-player world-control world-generator
