# Installer Build Guide

This project uses `jpackage` to build native installers from the shaded JAR (`target/hic-studio.jar`).
Each packaged app also includes:

- `Templates/HIC_Program_Label_Template.docx`
- `Templates/HIC_Signout_Template.xlsx`

## Prerequisites

- JDK 17+ (must include `jpackage`)
- Maven (`mvn`)

Check tools:

```bash
java -version
jpackage --version
mvn -version
```

## Build Commands

Run from project root.

macOS (DMG):

```bash
./build-mac.sh 1.0.0
```

Icon source for macOS:

- `src/main/resources/HIC_LOGO.png`
- The script auto-generates `packaging/mac/HIC_Studio.icns`
- For best quality, use a square PNG at `1024x1024`

Windows (MSI):

```powershell
powershell -ExecutionPolicy Bypass -File .\build-windows.ps1 -AppVersion 1.0.0
```

Icon source for Windows:

- Source PNG: `src/main/resources/HIC_LOGO.png`
- ICO target: `packaging/windows/HIC_Studio.ico`
- If `.ico` is missing, script auto-generates it when ImageMagick (`magick`) is installed

Linux (DEB default):

```bash
./build-linux.sh 1.0.0
```

Linux (RPM):

```bash
./build-linux.sh 1.0.0 rpm
```

Icon source for Linux:

- `src/main/resources/HIC_LOGO.png`

## Output

Installers are written to:

- `dist/`

The generated filename depends on OS and `jpackage` conventions.
