#!/bin/bash

# brew install mongodb/brew/mongodb-database-tools

DB=world
URL="mongodb://root:example@localhost:27017/${DB}?authSource=admin"

CP=$(dirname "$0")
BACKUP_DIR="${CP}/../../exports/backup_$(date +%Y%m%d_%H%M%S)"

COLLECTIONS=$(mongosh $URL --quiet --eval 'db.getCollectionNames().forEach(function(name) { print(name) })')

for COLLECTION in $COLLECTIONS; do
  echo "Backing up collection: $COLLECTION"
  BACKUP_FILE="${BACKUP_DIR}/${COLLECTION}.ndjson"
  mongoexport \
    --uri="mongodb://root:example@localhost:27017/${DB}?authSource=admin" \
    --collection=$COLLECTION \
    --out=$BACKUP_FILE
done