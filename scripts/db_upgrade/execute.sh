#!/bin/bash

DB=world

SCRIPT=$1

if [ -z "$SCRIPT" ]; then
  echo "Usage: $0 <script-file>"
  exit 1
fi
if [ ! -f "$SCRIPT" ]; then
  echo "Script file '$SCRIPT' does not exist."
  exit 1
fi

mongosh "mongodb://root:example@localhost:27017/${DB}?authSource=admin" --file ${SCRIPT}
