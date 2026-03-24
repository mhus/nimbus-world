#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IMAGE_TAG="${1:-latest}"
PLATFORM="${2:-}"

echo "Building nimbus-world-installer:${IMAGE_TAG}..."

if [ -n "$PLATFORM" ]; then
    docker buildx build \
        --platform "$PLATFORM" \
        -t "nimbus-world-installer:${IMAGE_TAG}" \
        --load \
        "$SCRIPT_DIR"
else
    docker build \
        -t "nimbus-world-installer:${IMAGE_TAG}" \
        "$SCRIPT_DIR"
fi

echo "Build completed: nimbus-world-installer:${IMAGE_TAG}"
