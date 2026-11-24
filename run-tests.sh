#!/usr/bin/env bash
# Runs the automated test suite (uses temporary data folders, never your shop data).
set -e
cd "$(dirname "$0")"
rm -rf out-test && mkdir -p out-test
javac -encoding UTF-8 -d out-test -cp "lib/*" $(find src test -name "*.java")
cp -r resources/. out-test/
exec java -Djava.awt.headless=true -cp "out-test:lib/*" com.smartbillingpro.TestRunner "$@"
