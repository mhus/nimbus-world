#!/bin/bash

set -e

# Default values
IMAGE_TAG="latest"
PLATFORM=""
SERVICES=("viewer" "editor" "controls")

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
            echo "Builds all frontend services (viewer, editor, controls) in one go."
            echo "The build stage is shared, so compilation happens only once."
            echo ""
            echo "Options:"
            echo "  --amd64         Build for AMD64 architecture"
            echo "  --arm64         Build for ARM64 architecture"
            echo "  --tag TAG       Set custom image tag (default: latest)"
            echo "  --help          Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                    # Build all services for local architecture"
            echo "  $0 --amd64            # Build all for AMD64"
            echo "  $0 --tag v1.0.0       # Build all with custom tag"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Get the project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
DOCKERFILE="server/deployment/docker-ts/Dockerfile"

echo "========================================"
echo "Building all frontend services"
echo "========================================"
echo "  Tag: ${IMAGE_TAG}"
echo "  Platform: ${PLATFORM:-local}"
echo "  Project root: ${PROJECT_ROOT}"
echo "  Services: ${SERVICES[@]}"
echo ""

cd "${PROJECT_ROOT}"

# Build each service
for service in "${SERVICES[@]}"; do
    IMAGE_NAME="nimbus-${service}"
    TARGET_STAGE="${service}"

    echo "----------------------------------------"
    echo "Building: ${service}"
    echo "  Image: ${IMAGE_NAME}:${IMAGE_TAG}"
    echo "  Target: ${TARGET_STAGE}"
    echo "----------------------------------------"

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

    echo "✓ ${IMAGE_NAME}:${IMAGE_TAG} built successfully"
    echo ""
done

echo "========================================"
echo "Build completed successfully!"
echo "========================================"
echo ""
echo "Built images:"
for service in "${SERVICES[@]}"; do
    echo "  - nimbus-${service}:${IMAGE_TAG}"
done
echo ""
echo "To run services:"
echo "  ./run-viewer-image.sh -d"
echo "  ./run-editor-image.sh -d"
echo "  ./run-controls-image.sh -d"
