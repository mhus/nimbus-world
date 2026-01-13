#!/bin/bash
set -e

cd $(dirname "$0")
cd ../..

# Setze Default-URLs für Zugriff auf Host-Dienste (host.docker.internal)
PLAYER_BASE_URL="${PLAYER_BASE_URL:-http://host.docker.internal:9042}"
LIFE_BASE_URL="${LIFE_BASE_URL:-http://host.docker.internal:9044}"
CONTROL_BASE_URL="${CONTROL_BASE_URL:-http://host.docker.internal:9043}"
MONGODB_URI="${MONGODB_URI:-mongodb://root:example@host.docker.internal:27017/world?authSource=admin}"
REDIS_URL="${REDIS_URL:-redis://host.docker.internal:6379}"
REDIS_HOST="${REDIS_HOST:-host.docker.internal}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_DATABASE="${REDIS_DATABASE:-0}"
REDIS_PASSWORD="${REDIS_PASSWORD:-}"
REDIS_SSL="${REDIS_SSL:-false}"

# Übergib Umgebungsvariablen an den Container, falls sie gesetzt sind
ENV_VARS=""
[ -n "$PLAYER_BASE_URL" ] && ENV_VARS="$ENV_VARS -e PLAYER_BASE_URL=$PLAYER_BASE_URL"
[ -n "$LIFE_BASE_URL" ] && ENV_VARS="$ENV_VARS -e LIFE_BASE_URL=$LIFE_BASE_URL"
[ -n "$CONTROL_BASE_URL" ] && ENV_VARS="$ENV_VARS -e CONTROL_BASE_URL=$CONTROL_BASE_URL"
[ -n "$MONGODB_URI" ] && ENV_VARS="$ENV_VARS -e MONGODB_URI=$MONGODB_URI"
[ -n "$REDIS_URL" ] && ENV_VARS="$ENV_VARS -e REDIS_URL=$REDIS_URL"
[ -n "$REDIS_HOST" ] && ENV_VARS="$ENV_VARS -e REDIS_HOST=$REDIS_HOST"
[ -n "$REDIS_PORT" ] && ENV_VARS="$ENV_VARS -e REDIS_PORT=$REDIS_PORT"
[ -n "$REDIS_DATABASE" ] && ENV_VARS="$ENV_VARS -e REDIS_DATABASE=$REDIS_DATABASE"
[ -n "$REDIS_PASSWORD" ] && ENV_VARS="$ENV_VARS -e REDIS_PASSWORD=$REDIS_PASSWORD"
[ -n "$REDIS_SSL" ] && ENV_VARS="$ENV_VARS -e REDIS_SSL=$REDIS_SSL"

# Kein benutzerdefiniertes Netzwerk nötig für host.docker.internal
NETWORK=""

echo "Starte world-player-native Container auf Port 9042 (host.docker.internal)..."
eval docker run --rm -p 9042:9042 --name world-player-native $NETWORK $ENV_VARS world-player-native
