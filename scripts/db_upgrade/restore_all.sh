#!/bin/bash

# brew install mongodb/brew/mongodb-database-tools

DB=world
URL="mongodb://root:example@localhost:27017/${DB}?authSource=admin"

# Backup directory to restore from
# Example: exports/backup_20250126_143022
BACKUP_DIR="${1}"

if [ -z "$BACKUP_DIR" ]; then
  echo "Usage: $0 <backup_directory>"
  echo "Example: $0 exports/backup_20250126_143022"
  exit 1
fi

if [ ! -d "$BACKUP_DIR" ]; then
  echo "Error: Backup directory '$BACKUP_DIR' does not exist"
  exit 1
fi

echo "Restoring from backup: $BACKUP_DIR"
echo "Database: $DB"
echo ""

# Find all .ndjson files in backup directory
for BACKUP_FILE in "$BACKUP_DIR"/*.ndjson; do
  if [ ! -f "$BACKUP_FILE" ]; then
    echo "No backup files found in $BACKUP_DIR"
    exit 1
  fi

  # Extract collection name from filename
  COLLECTION=$(basename "$BACKUP_FILE" .ndjson)

  echo "Restoring collection: $COLLECTION"

  # Drop existing collection
  mongosh $URL --quiet --eval "db.${COLLECTION}.drop()" > /dev/null 2>&1

  # Import collection
  mongoimport \
    --uri="mongodb://root:example@localhost:27017/${DB}?authSource=admin" \
    --collection=$COLLECTION \
    --file=$BACKUP_FILE

  echo ""
done

echo "Restore completed!"
