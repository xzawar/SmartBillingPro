#!/usr/bin/env bash
# Builds a runnable jar bundle (any OS) and, if jpackage is available, a native app image for this OS.
# Windows .exe builds use build-exe.bat (jpackage only builds for the OS it runs on).
set -e
cd "$(dirname "$0")"
VERSION=4.0.0
rm -rf build dist && mkdir -p build/classes build/input dist
javac -encoding UTF-8 --release 17 -d build/classes -cp "lib/*" $(find src -name "*.java")
cp -r resources/. build/classes/
jar --create --file build/input/SmartBillingPro.jar --main-class com.smartbillingpro.SmartBillingApp -C build/classes .
cp lib/*.jar build/input/
mkdir -p dist/SmartBillingPro-$VERSION && cp build/input/*.jar dist/SmartBillingPro-$VERSION/
cat > dist/SmartBillingPro-$VERSION/start.sh <<'S'
#!/usr/bin/env bash
cd "$(dirname "$0")" && exec java -Dfile.encoding=UTF-8 -cp "SmartBillingPro.jar:*" com.smartbillingpro.SmartBillingApp
S
chmod +x dist/SmartBillingPro-$VERSION/start.sh
printf '@echo off\r\ncd /d "%%~dp0"\r\nstart "" javaw -Dfile.encoding=UTF-8 -cp "SmartBillingPro.jar;*" com.smartbillingpro.SmartBillingApp\r\n' > dist/SmartBillingPro-$VERSION/start.bat
(cd dist && zip -qr SmartBillingPro-$VERSION-java.zip SmartBillingPro-$VERSION)
echo "Built dist/SmartBillingPro-$VERSION-java.zip (needs Java 17+ installed)"
if command -v jpackage >/dev/null && [ "${SKIP_JPACKAGE:-}" = "" ]; then
  jpackage --type app-image --name SmartBillingPro --app-version $VERSION --input build/input \
    --main-jar SmartBillingPro.jar --main-class com.smartbillingpro.SmartBillingApp \
    --icon packaging/app.png --java-options "-Dfile.encoding=UTF-8" --dest dist
  echo "Built native app image in dist/SmartBillingPro"
fi
