#!/bin/sh
set -e

# Docker entrypoint script for nimbus-controls
# Generates config.json from environment variables at runtime

CONFIG_FILE="/usr/share/nginx/html/controls/config.json"

# Default values
API_URL="${VITE_CONTROL_API_URL:-http://localhost:9043}"

echo "Generating runtime configuration..."
echo "API_URL: $API_URL"

# Generate config.json
cat > "$CONFIG_FILE" <<EOF
{
  "apiUrl": "$API_URL"
}
EOF

# Set read permissions for all users
chmod 644 "$CONFIG_FILE"

echo "Runtime configuration generated at $CONFIG_FILE"
cat "$CONFIG_FILE"

# Execute the main container command (typically nginx)
exec "$@"
