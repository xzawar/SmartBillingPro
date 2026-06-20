#!/usr/bin/env bash
# Smart Billing Pro - ADMIN CONSOLE ONLY (compile and run on macOS / Linux)
set -e
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
  echo "ERROR: 'javac' not found. Install a JDK 17+ (https://adoptium.net) and try again."
  exit 1
fi

mkdir -p out
echo "Compiling sources..."
find src -name "*.java" > sources.txt
javac -d out -cp "lib/*" @sources.txt
rm -f sources.txt

echo "Starting the Admin Console..."
java -cp "out:lib/*" com.smartbillingpro.AdminApp
