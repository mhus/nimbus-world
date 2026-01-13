#!/bin/bash

set -e

# Default values
IMAGE_NAME="nimbus-world-player"
IMAGE_TAG="latest"
CONTAINER_NAME="nimbus-world-player"
HOST_PORT="9042"
CONTAINER_PORT="9042"
DETACH=""
EXTRA_ARGS=""

# Default environment variables from application.yaml
DEFAULT_ENV=(
    "MONGODB_URI=mongodb://root:example@host.docker.internal:27017/world?authSource=admin"
    "REDIS_URL=redis://host.docker.internal:6379"
    "REDIS_HOST=host.docker.internal"
    "REDIS_PORT=6379"
    "REDIS_DATABASE=0"
    "REDIS_PASSWORD="
    "REDIS_SSL=false"
    "PLAYER_BASE_URL=http://host.docker.internal:9042"
    "LIFE_BASE_URL=http://host.docker.internal:9044"
    "CONTROL_BASE_URL=http://host.docker.internal:9043"
    "NIMBUS_ENCRYPTION_PASSWORD=changeme"
    "CHUNK_COMPRESSION_ENABLED=true"
    "LAYER_TERRAIN_COMPRESSION_ENABLED=true"
    "ASSET_COMPRESSION_ENABLED=true"
    "NIMBUS_SCHEMA_AUTO_MIGRATE=false"
)

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --tag)
            IMAGE_TAG="$2"
            shift 2
            ;;
        --name)
            CONTAINER_NAME="$2"
            shift 2
            ;;
        --port)
            HOST_PORT="$2"
            CONTAINER_PORT="$2"
            shift 2
            ;;
        -d|--detach)
            DETACH="-d"
            shift
            ;;
        --env-file)
            EXTRA_ARGS="${EXTRA_ARGS} --env-file $2"
            shift 2
            ;;
        -e|--env)
            EXTRA_ARGS="${EXTRA_ARGS} -e $2"
            shift 2
            ;;
        --network)
            EXTRA_ARGS="${EXTRA_ARGS} --network $2"
            shift 2
            ;;
        --memory)
            EXTRA_ARGS="${EXTRA_ARGS} --memory $2"
            shift 2
            ;;
        --cpus)
            EXTRA_ARGS="${EXTRA_ARGS} --cpus $2"
            shift 2
            ;;
        --help)
            echo "Usage: $0 [options]"
            echo ""
            echo "Options:"
            echo "  --tag TAG           Image tag to run (default: latest)"
            echo "  --name NAME         Container name (default: nimbus-world-player)"
            echo "  --port PORT         Host port to bind (default: 9042)"
            echo "  -d, --detach        Run container in background"
            echo "  --env-file FILE     Read environment variables from file (overrides defaults)"
            echo "  -e, --env VAR=VAL   Set environment variable (overrides defaults)"
            echo "  --network NETWORK   Connect to network"
            echo "  --memory LIMIT      Memory limit (e.g., 2g)"
            echo "  --cpus NUMBER       Number of CPUs (e.g., 2.0)"
            echo "  --help              Show this help message"
            echo ""
            echo "Default environment variables:"
            echo "  MONGODB_URI=mongodb://root:example@host.docker.internal:27017/world?authSource=admin"
            echo "  REDIS_URL=redis://host.docker.internal:6379"
            echo "  NIMBUS_ENCRYPTION_PASSWORD=changeme"
            echo "  (and more - see script for full list)"
            echo ""
            echo "Examples:"
            echo "  $0                                    # Run interactively on port 9042"
            echo "  $0 -d                                 # Run in background"
            echo "  $0 --port 9090                        # Run on port 9090"
            echo "  $0 --memory 2g --cpus 2.0             # Run with resource limits"
            echo "  $0 -e MONGODB_URI=mongodb://...       # Override MongoDB URI"
            echo "  $0 --env-file .env                    # Use custom env file"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Stop and remove existing container if it exists
if docker ps -a --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "Stopping and removing existing container: ${CONTAINER_NAME}"
    docker stop "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    docker rm "${CONTAINER_NAME}" >/dev/null 2>&1 || true
fi

echo "Starting Docker container..."
echo "  Image: ${IMAGE_NAME}:${IMAGE_TAG}"
echo "  Container: ${CONTAINER_NAME}"
echo "  Port mapping: ${HOST_PORT}:${CONTAINER_PORT}"
if [ -n "$DETACH" ]; then
    echo "  Mode: Detached (background)"
else
    echo "  Mode: Interactive"
fi
echo ""

# Build environment arguments (only add if not already in EXTRA_ARGS)
ENV_ARGS=""
if [[ ! "$EXTRA_ARGS" =~ "--env-file" ]]; then
    for env_var in "${DEFAULT_ENV[@]}"; do
        ENV_ARGS="${ENV_ARGS} -e ${env_var}"
    done
fi

# Run Docker container
docker run \
    ${DETACH} \
    --name "${CONTAINER_NAME}" \
    -p "${HOST_PORT}:${CONTAINER_PORT}" \
    ${ENV_ARGS} \
    ${EXTRA_ARGS} \
    "${IMAGE_NAME}:${IMAGE_TAG}"

if [ -n "$DETACH" ]; then
    echo ""
    echo "Container started successfully!"
    echo ""
    echo "Useful commands:"
    echo "  docker logs ${CONTAINER_NAME}           # View logs"
    echo "  docker logs -f ${CONTAINER_NAME}        # Follow logs"
    echo "  docker stop ${CONTAINER_NAME}           # Stop container"
    echo "  docker restart ${CONTAINER_NAME}        # Restart container"
    echo "  docker exec -it ${CONTAINER_NAME} bash  # Open shell"
    echo ""
    echo "Application should be available at: http://localhost:${HOST_PORT}"
    echo "Health check: http://localhost:${HOST_PORT}/actuator/health"
fi
