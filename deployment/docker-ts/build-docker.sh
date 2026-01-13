#!/bin/bash

set -e

# Default values
SERVICE="viewer"
IMAGE_TAG="latest"
PLATFORM=""

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        viewer|editor|controls)
            SERVICE="$1"
            shift
            ;;
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
            echo "Usage: $0 [service] [options]"
            echo ""
            echo "Services:"
            echo "  viewer          Build engine viewer (default)"
            echo "  editor          Build engine editor"
            echo "  controls        Build controls package"
            echo ""
            echo "Options:"
            echo "  --amd64         Build for AMD64 architecture"
            echo "  --arm64         Build for ARM64 architecture"
            echo "  --tag TAG       Set custom image tag (default: latest)"
            echo "  --help          Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0 viewer             # Build viewer for local architecture"
            echo "  $0 editor             # Build editor"
            echo "  $0 viewer --amd64     # Build viewer for AMD64"
            echo "  $0 controls --tag v1.0.0   # Build controls with custom tag"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Set image name based on service
IMAGE_NAME="nimbus-${SERVICE}"
DOCKERFILE="server/deployment/docker-ts/Dockerfile"
TARGET_STAGE="${SERVICE}"

# Get the project root directory (3 levels up from docker-ts to nimbus-server root)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

echo "Building Docker image..."
echo "  Service: ${SERVICE}"
echo "  Image: ${IMAGE_NAME}:${IMAGE_TAG}"
echo "  Target stage: ${TARGET_STAGE}"
echo "  Platform: ${PLATFORM:-local}"
echo "  Project root: ${PROJECT_ROOT}"
echo ""

# Build Docker image from nimbus-server root
cd "${PROJECT_ROOT}"

if [ -z "$PLATFORM" ]; then
    # Build for local architecture
    docker build \
        -f "${DOCKERFILE}" \
        --target "${TARGET_STAGE}" \
        -t "${IMAGE_NAME}:${IMAGE_TAG}" \
        .
else
    # Build for specific platform
    docker buildx build \
        --platform "${PLATFORM}" \
        -f "${DOCKERFILE}" \
        --target "${TARGET_STAGE}" \
        -t "${IMAGE_NAME}:${IMAGE_TAG}" \
        --load \
        .
fi

echo ""
echo "Build completed successfully!"
echo "Image: ${IMAGE_NAME}:${IMAGE_TAG}"
echo ""
echo "To run the image:"
echo "  ./run-${SERVICE}-image.sh"
