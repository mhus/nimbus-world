#!/bin/bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

IMAGE_TAG="latest"
PLATFORM=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --amd64)
            PLATFORM="linux/amd64"
            IMAGE_TAG="${IMAGE_TAG}-amd64"
            shift
            ;;
        --arm64)
            PLATFORM="linux/arm64"
            IMAGE_TAG="${IMAGE_TAG}-arm64"
            shift
            ;;
        --tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Builds the nimbus-world-installer image."
            echo ""
            echo "Options:"
            echo "  --amd64         Build for AMD64 architecture (tag suffix -amd64)"
            echo "  --arm64         Build for ARM64 architecture (tag suffix -arm64)"
            echo "  --tag TAG       Set custom image tag (default: latest)"
            echo "  --help          Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

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
