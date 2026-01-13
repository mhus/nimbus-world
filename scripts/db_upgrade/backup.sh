#!/bin/bash

# brew install mongodb/brew/mongodb-database-tools

DB=world
COLLECTION=s_assets

CP=$(dirname "$0")
BACKUP_FILE="${CP}/../../exports/backup_${COLLECTION}_$(date +%Y%m%d_%H%M%S).ndjson"
mongoexport \
  --uri="mongodb://root:example@localhost:27017/${DB}?authSource=admin" \
  --collection=$COLLECTION \
  --out=$BACKUP_FILE
