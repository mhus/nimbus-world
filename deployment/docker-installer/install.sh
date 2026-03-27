#!/bin/bash
set -e

# Nimbus World Database Installer
# Downloads a MongoDB backup from Google Drive and restores it.
#
# Usage:
#   docker run --rm nimbus-world-installer \
#     --url "https://drive.google.com/file/d/FILE_ID/view?usp=sharing" \
#     --mongodb-uri "mongodb://root:password@host:27017/dbname?authSource=admin"
#
# Or with file ID directly:
#   docker run --rm nimbus-world-installer \
#     --id "1X31HkMn_EZBYAla7jApYxrvRlqf6tAtE" \
#     --mongodb-uri "mongodb://root:password@host:27017/dbname?authSource=admin"

GOOGLE_DRIVE_URL=""
GOOGLE_DRIVE_ID=""
MONGODB_URI="${MONGODB_URI:-}"
PRESERVE_SETTINGS="${PRESERVE_SETTINGS:-true}"
WORK_DIR="/tmp/installer"
SETTINGS_FILE="$WORK_DIR/s_settings_backup.ndjson"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --url)
            GOOGLE_DRIVE_URL="$2"
            shift 2
            ;;
        --id)
            GOOGLE_DRIVE_ID="$2"
            shift 2
            ;;
        --mongodb-uri)
            MONGODB_URI="$2"
            shift 2
            ;;
        --help)
            echo "Nimbus World Database Installer"
            echo ""
            echo "Usage: nimbus-world-installer [options]"
            echo ""
            echo "Options:"
            echo "  --url URL           Google Drive share URL"
            echo "  --id FILE_ID        Google Drive file ID"
            echo "  --mongodb-uri URI   MongoDB connection URI"
            echo "  --help              Show this help"
            echo ""
            echo "Environment variables:"
            echo "  MONGODB_URI         MongoDB connection URI (alternative to --mongodb-uri)"
            echo "  PRESERVE_SETTINGS   Preserve s_settings collection across restore (default: true)"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

# Extract file ID from URL if provided
if [ -n "$GOOGLE_DRIVE_URL" ] && [ -z "$GOOGLE_DRIVE_ID" ]; then
    GOOGLE_DRIVE_ID=$(echo "$GOOGLE_DRIVE_URL" | grep -oP '(?<=/d/)[^/]+' || echo "")
    if [ -z "$GOOGLE_DRIVE_ID" ]; then
        GOOGLE_DRIVE_ID=$(echo "$GOOGLE_DRIVE_URL" | grep -oP '(?<=id=)[^&]+' || echo "")
    fi
fi

# Validate inputs
if [ -z "$GOOGLE_DRIVE_ID" ]; then
    echo "Error: No Google Drive file ID provided. Use --url or --id"
    exit 1
fi

if [ -z "$MONGODB_URI" ]; then
    echo "Error: No MongoDB URI provided. Use --mongodb-uri or set MONGODB_URI"
    exit 1
fi

echo "========================================"
echo "Nimbus World Database Installer"
echo "========================================"
echo "  File ID: $GOOGLE_DRIVE_ID"
echo "  MongoDB: ${MONGODB_URI%%@*}@***"
echo "  Preserve settings: $PRESERVE_SETTINGS"
echo ""

# Create work directory
mkdir -p "$WORK_DIR"
cd "$WORK_DIR"

# Step 1: Download from Google Drive
echo "[1/4] Downloading from Google Drive..."
gdown "$GOOGLE_DRIVE_ID" -O backup.zip
if [ ! -f backup.zip ]; then
    echo "Error: Download failed"
    exit 1
fi
echo "  Downloaded: $(du -h backup.zip | cut -f1)"

# Step 2: Unzip
echo ""
echo "[2/4] Extracting backup..."
unzip -o backup.zip -d backup
echo "  Extracted to: $WORK_DIR/backup"

# Step 3: Find backup directory (may be nested)
BACKUP_DIR=$(find backup -name "*.ndjson" -printf '%h\n' 2>/dev/null | head -1)
if [ -z "$BACKUP_DIR" ]; then
    BACKUP_DIR=$(find backup -name "*.bson" -printf '%h\n' 2>/dev/null | head -1)
fi

if [ -z "$BACKUP_DIR" ]; then
    echo "Error: No backup files (.ndjson or .bson) found in archive"
    echo "Archive contents:"
    find backup -type f | head -20
    exit 1
fi
echo "  Backup directory: $BACKUP_DIR"

# Step 4: Preserve s_settings if enabled
if [ "$PRESERVE_SETTINGS" = "true" ]; then
    echo ""
    echo "[3/6] Backing up s_settings collection..."
    if mongoexport --uri="$MONGODB_URI" --collection="s_settings" --out="$SETTINGS_FILE" 2>/dev/null; then
        echo "  Saved $(wc -l < "$SETTINGS_FILE") documents"
    else
        echo "  No existing s_settings collection found (skipping)"
        rm -f "$SETTINGS_FILE"
    fi
fi

# Step 5: Restore
echo ""
echo "[4/6] Restoring database..."

NDJSON_COUNT=$(find "$BACKUP_DIR" -name "*.ndjson" | wc -l)
BSON_COUNT=$(find "$BACKUP_DIR" -name "*.bson" | wc -l)

if [ "$NDJSON_COUNT" -gt 0 ]; then
    echo "  Format: NDJSON ($NDJSON_COUNT collections)"
    for BACKUP_FILE in "$BACKUP_DIR"/*.ndjson; do
        COLLECTION=$(basename "$BACKUP_FILE" .ndjson)
        echo "  Restoring: $COLLECTION"
        mongosh "$MONGODB_URI" --quiet --eval "db.${COLLECTION}.drop()" > /dev/null 2>&1 || true
        mongoimport --uri="$MONGODB_URI" --collection="$COLLECTION" --file="$BACKUP_FILE"
    done
elif [ "$BSON_COUNT" -gt 0 ]; then
    echo "  Format: BSON ($BSON_COUNT collections)"
    DB_NAME=$(echo "$MONGODB_URI" | sed 's|.*/||' | sed 's|\?.*||')
    mongorestore --uri="$MONGODB_URI" --db="$DB_NAME" --drop "$BACKUP_DIR"
else
    echo "Error: No supported backup format found"
    exit 1
fi

# Step 6: Restore preserved s_settings
if [ "$PRESERVE_SETTINGS" = "true" ] && [ -f "$SETTINGS_FILE" ]; then
    echo ""
    echo "[5/6] Restoring preserved s_settings..."
    mongoimport --uri="$MONGODB_URI" --collection="s_settings" --mode=upsert --file="$SETTINGS_FILE"
    echo "  s_settings restored"
fi

# Step 7: Cleanup
echo ""
echo "[6/6] Cleanup..."
rm -rf "$WORK_DIR"

echo ""
echo "========================================"
echo "Restore completed successfully!"
echo "========================================"
