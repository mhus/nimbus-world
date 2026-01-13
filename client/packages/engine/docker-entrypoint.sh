#!/bin/sh
set -e

# Docker entrypoint script for nimbus-engine
# Generates config.json from environment variables at runtime

CONFIG_FILE="/usr/share/nginx/html/config.json"
CONFIG_FILE_VIEWER="/usr/share/nginx/html/viewer/config.json"
CONFIG_FILE_EDITOR="/usr/share/nginx/html/editor/config.json"

# Default values
API_URL="${VITE_SERVER_API_URL:-http://localhost:9042}"
EXIT_URL="${VITE_EXIT_URL:-/login}"

echo "Generating runtime configuration for Nimbus Engine..."
echo "API_URL: $API_URL"
echo "EXIT_URL: $EXIT_URL"

# Generate config.json content
CONFIG_CONTENT=$(cat <<EOF
{
  "apiUrl": "$API_URL",
  "exitUrl": "$EXIT_URL"
}
EOF
)

# Write to root and base path locations
echo "$CONFIG_CONTENT" > "$CONFIG_FILE"
mkdir -p "$(dirname "$CONFIG_FILE_VIEWER")"
echo "$CONFIG_CONTENT" > "$CONFIG_FILE_VIEWER"
mkdir -p "$(dirname "$CONFIG_FILE_EDITOR")"
echo "$CONFIG_CONTENT" > "$CONFIG_FILE_EDITOR"

# Set proper permissions and ownership for nginx user
chmod 644 "$CONFIG_FILE"
chmod 644 "$CONFIG_FILE_VIEWER"
chmod 644 "$CONFIG_FILE_EDITOR"
chmod 755 "$(dirname "$CONFIG_FILE_VIEWER")"
chmod 755 "$(dirname "$CONFIG_FILE_EDITOR")"
chown nginx:nginx "$CONFIG_FILE"
chown -R nginx:nginx "$(dirname "$CONFIG_FILE_VIEWER")"
chown -R nginx:nginx "$(dirname "$CONFIG_FILE_EDITOR")"

echo "Runtime configuration generated at $CONFIG_FILE, $CONFIG_FILE_VIEWER, and $CONFIG_FILE_EDITOR"
cat "$CONFIG_FILE"

# Note:
# - worldId is passed via URL parameter (?worldId=xxx)
# - websocketUrl comes from server config (/player/world/config)

# Execute the main container command (typically nginx)
exec "$@"
