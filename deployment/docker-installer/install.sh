#!/bin/bash
set -e

echo "Installation script is running..."

# Example: Show MongoDB Tools version
mongodump --version

# Example: Test unzip and wget
unzip -v
wget --version

echo "install.sh completed successfully."
