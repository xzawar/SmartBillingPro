#!/usr/bin/env bash
# Smart Billing Pro - compile and run from source (macOS / Linux). Needs JDK 17+.
set -e
cd "$(dirname "$0")"
command -v javac >/dev/null || { echo "ERROR: javac not found. Install a JDK 17+ (https://adoptium.net)."; exit 1; }
rm -rf out && mkdir -p out
echo "Compiling..."
javac -encoding UTF-8 -d out -cp "lib/*" $(find src -name "*.java")
cp -r resources/. out/
echo "Starting Smart Billing Pro..."
exec java -Dfile.encoding=UTF-8 -cp "out:lib/*" com.smartbillingpro.SmartBillingApp
