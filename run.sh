#!/usr/bin/env bash
# Smart Billing Pro - compile and run (macOS / Linux)
set -e
cd "$(dirname "$0")"

if ! command -v javac >/dev/null 2>&1; then
  echo "ERROR: 'javac' not found. Install a JDK 17+ (https://adoptium.net) and try again."
  exit 1
fi

if ! ls lib/*.jar >/dev/null 2>&1; then
  echo "WARNING: No JDBC driver found in lib/."
  echo "Download sqlite-jdbc from https://github.com/xerial/sqlite-jdbc/releases"
  echo "and drop the .jar into the lib/ folder before running."
  echo
fi

mkdir -p out
echo "Compiling sources..."
find src -name "*.java" > sources.txt
javac -d out -cp "lib/*" @sources.txt
rm -f sources.txt

echo "Starting Smart Billing Pro..."
java -cp "out:lib/*" com.smartbillingpro.SmartBillingApp
