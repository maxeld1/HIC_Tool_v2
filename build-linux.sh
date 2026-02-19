#!/usr/bin/env bash
set -euo pipefail

APP_NAME="HIC Studio"
APP_VERSION="${1:-1.0.0}"
MAIN_CLASS="hic.Main2"
VENDOR="HIC Studio"
INSTALLER_TYPE="${2:-deb}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
TARGET_DIR="$PROJECT_ROOT/target"
JAR_PATH="$TARGET_DIR/hic-studio.jar"
DEST_DIR="$PROJECT_ROOT/dist"
ICON_PNG="$PROJECT_ROOT/src/main/resources/HIC_LOGO.png"
LABEL_TEMPLATE="$PROJECT_ROOT/HIC_Program_Label_Template.docx"
SIGNOUT_TEMPLATE="$PROJECT_ROOT/HIC_Signout_Template.xlsx"

if [[ "$OSTYPE" != linux* ]]; then
  echo "Error: build-linux.sh must be run on Linux."
  exit 1
fi

if [[ "$INSTALLER_TYPE" != "deb" && "$INSTALLER_TYPE" != "rpm" ]]; then
  echo "Error: installer type must be 'deb' or 'rpm' (got '$INSTALLER_TYPE')."
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Error: mvn not found. Install Maven first."
  exit 1
fi

if ! command -v jpackage >/dev/null 2>&1; then
  echo "Error: jpackage not found. Install JDK 17+ first."
  exit 1
fi

if [[ ! "$APP_VERSION" =~ ^[0-9]+(\.[0-9]+){0,2}$ ]]; then
  echo "Error: app version must be numeric like 1, 1.2, or 1.2.3 (got '$APP_VERSION')."
  exit 1
fi

if [[ ! -f "$ICON_PNG" ]]; then
  echo "Error: icon source not found at $ICON_PNG."
  exit 1
fi

if [[ ! -f "$LABEL_TEMPLATE" ]]; then
  echo "Error: label template not found at $LABEL_TEMPLATE."
  exit 1
fi

if [[ ! -f "$SIGNOUT_TEMPLATE" ]]; then
  echo "Error: sign-out template not found at $SIGNOUT_TEMPLATE."
  exit 1
fi

cd "$PROJECT_ROOT"

echo "Building shaded JAR..."
mvn clean package -DskipTests

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Error: $JAR_PATH not found after build."
  exit 1
fi

mkdir -p "$DEST_DIR"
TMP_WORKDIR="$(mktemp -d "$DEST_DIR/hic.pkg.work.XXXXXX")"
STAGED_INPUT="$TMP_WORKDIR/input"
mkdir -p "$STAGED_INPUT/Templates"

cp "$JAR_PATH" "$STAGED_INPUT/"
cp "$LABEL_TEMPLATE" "$STAGED_INPUT/Templates/"
cp "$SIGNOUT_TEMPLATE" "$STAGED_INPUT/Templates/"

echo "Creating Linux $INSTALLER_TYPE installer..."
jpackage \
  --name "$APP_NAME" \
  --input "$STAGED_INPUT" \
  --main-jar "hic-studio.jar" \
  --main-class "$MAIN_CLASS" \
  --type "$INSTALLER_TYPE" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --icon "$ICON_PNG" \
  --dest "$DEST_DIR"

rm -rf "$TMP_WORKDIR"

echo "Done. Installer created in: $DEST_DIR"
