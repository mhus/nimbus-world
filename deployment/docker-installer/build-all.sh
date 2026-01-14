#!/bin/bash

set -e

# Default values
IMAGE_TAG="latest"
PLATFORM=""
IMAGE_NAME="nimbus-world-installer"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKERFILE="${SCRIPT_DIR}/Dockerfile"

# Parse command line arguments
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
            echo "Builds the nimbus-world-installer Docker image."
            echo ""
            echo "Options:"
            echo "  --amd64         Build for AMD64 architecture"
            echo "  --arm64         Build for ARM64 architecture"
            echo "  --tag TAG       Set custom image tag (default: latest)"
            echo "  --help          Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                    # Build for local architecture"
            echo "  $0 --amd64            # Build for AMD64"
            echo "  $0 --tag v1.0.0       # Build with custom tag"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

PROJECT_ROOT="${SCRIPT_DIR}"

echo "========================================"
echo "Building nimbus-world-installer Docker image"
echo "========================================"
echo "  Tag: ${IMAGE_TAG}"
echo "  Platform: ${PLATFORM:-local}"
echo "  Project root: ${PROJECT_ROOT}"
echo "  Dockerfile: ${DOCKERFILE}"
echo ""

cd "${PROJECT_ROOT}"

if [ -z "$PLATFORM" ]; then
    # Build for local architecture
    docker build \
        -f "${DOCKERFILE}" \
        -t "${IMAGE_NAME}:${IMAGE_TAG}" \
        .
else
    # Build for specific platform
    docker buildx build \
        --platform "${PLATFORM}" \
        -f "${DOCKERFILE}" \
        -t "${IMAGE_NAME}:${IMAGE_TAG}" \
        --load \
        .
fi

echo "✓ ${IMAGE_NAME}:${IMAGE_TAG} built successfully"
echo ""
echo "========================================"
echo "Build completed successfully!"
echo "========================================"
echo ""
echo "Built image: ${IMAGE_NAME}:${IMAGE_TAG}"
echo ""
echo "To run the installer image:"
echo "  docker run --rm ${IMAGE_NAME}:${IMAGE_TAG}"
