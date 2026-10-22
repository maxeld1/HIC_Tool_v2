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

## Update prompts and GitHub releases

Packaged Windows and Mac apps check `maxeld1/HIC_Tool_v2` for a newer published
GitHub release shortly after startup. Users can also choose **Help > Check for
Updates**. Checking or downloading never installs an update without the user's
confirmation. Network failures do not prevent normal use.

The release repository must be public for these unauthenticated checks and downloads.
Private repositories need a separate accessible release feed; do not embed a GitHub
access token in the desktop app. The repository is configured in
`src/main/resources/hic-update.properties`.

### Publishing an update

1. Commit and push the code, including `.github/workflows/release.yml`.
2. Create and push a new numeric version tag, for example:

   ```bash
   git tag v1.0.1
   git push origin v1.0.1
   ```

3. GitHub Actions builds installers for Windows x64, Intel Macs, and Apple Silicon
   Macs and attaches them to a **draft release**. All three builds must succeed.
4. Download and test the installers from the draft, edit the release notes, and
   publish the release when ready. Installed apps will offer it on their next launch.

Ordinary code pushes do not notify installed users. Only publishing a full release
with a higher version does. Drafts and prereleases are ignored. Keep release tags
and installer versions in sync, using `major.minor.patch` versions. The build scripts
embed the supplied version in the application; local Maven builds default to `1.0.0`.

The updater selects the asset matching the operating system and Java runtime
architecture. On an Apple Silicon Mac running the Intel app under Rosetta, it
selects the Intel installer. Expected release filenames are:

- `HIC-Studio-1.0.1-windows-x64.msi`
- `HIC-Studio-1.0.1-macos-x64.dmg`
- `HIC-Studio-1.0.1-macos-arm64.dmg`

GitHub must provide the asset's SHA-256 digest. The updater verifies the downloaded
file before offering to open it. If installation is deferred, the dialog shows the
downloaded installer's location for later use.

### Installing updates

Install the first updater-enabled version on each computer manually. Older versions
cannot discover updates by themselves.

- **Windows:** Accepting installation closes HIC Studio and opens the MSI. Follow
  its prompts and reopen the app. Future installers share a fixed upgrade UUID.
  An older installation made before this UUID was introduced may need one final
  manual uninstall during migration. Do not change the UUID in later builds.
- **Mac:** Accepting installation closes HIC Studio and opens the DMG. Drag HIC
  Studio into Applications and choose Replace, then reopen it. This is a guided
  installer flow, not an unattended replacement of the running application.

Finish work before choosing Install. The updater does not delete settings or output
files. Keep output files outside the app bundle, which is replaced on Mac.

These scripts do not yet sign Windows installers or sign/notarize Mac releases.
OS security prompts can still appear; signing requires your developer certificates
and a separate signing configuration. Verify Windows upgrades and both Mac packages
on real computers before publishing the first release.
