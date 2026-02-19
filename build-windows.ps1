param(
    [string]$AppVersion = "1.0.0"
)

$ErrorActionPreference = "Stop"

$appName = "HIC Studio"
$mainClass = "hic.Main2"
$vendor = "HIC Studio"
$scriptDir = Split-Path -Path $MyInvocation.MyCommand.Path -Parent
$projectRoot = Resolve-Path (Join-Path $scriptDir ".")
$targetDir = Join-Path $projectRoot "target"
$jarPath = Join-Path $targetDir "hic-studio.jar"
$destDir = Join-Path $projectRoot "dist"
$resourcesDir = Join-Path $projectRoot "packaging\\windows"
$iconPng = Join-Path $projectRoot "src\\main\\resources\\HIC_LOGO.png"
$iconIco = Join-Path $resourcesDir "HIC_Studio.ico"
$labelTemplate = Join-Path $projectRoot "HIC_Program_Label_Template.docx"
$signOutTemplate = Join-Path $projectRoot "HIC_Signout_Template.xlsx"
$stagingRoot = Join-Path $destDir "jpackage-input"
$stagedInput = Join-Path $stagingRoot ([Guid]::NewGuid().ToString())
$stagedTemplates = Join-Path $stagedInput "Templates"

if (-not $IsWindows) {
    throw "build-windows.ps1 must be run on Windows."
}

if ($AppVersion -notmatch '^[0-9]+(\.[0-9]+){0,2}$') {
    throw "AppVersion must be numeric like 1, 1.2, or 1.2.3 (got '$AppVersion')."
}

if (-not (Test-Path $iconPng)) {
    throw "Icon source not found at $iconPng."
}

if (-not (Test-Path $labelTemplate)) {
    throw "Label template not found at $labelTemplate."
}

if (-not (Test-Path $signOutTemplate)) {
    throw "Sign-out template not found at $signOutTemplate."
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "mvn not found. Install Maven first."
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage not found. Install JDK 17+ first."
}

Set-Location $projectRoot

Write-Host "Building shaded JAR..."
mvn clean package -DskipTests

if (-not (Test-Path $jarPath)) {
    throw "$jarPath not found after build."
}

New-Item -ItemType Directory -Path $destDir -Force | Out-Null
New-Item -ItemType Directory -Path $resourcesDir -Force | Out-Null
New-Item -ItemType Directory -Path $stagedTemplates -Force | Out-Null

Copy-Item -Path $jarPath -Destination (Join-Path $stagedInput "hic-studio.jar") -Force
Copy-Item -Path $labelTemplate -Destination (Join-Path $stagedTemplates "HIC_Program_Label_Template.docx") -Force
Copy-Item -Path $signOutTemplate -Destination (Join-Path $stagedTemplates "HIC_Signout_Template.xlsx") -Force

if (-not (Test-Path $iconIco)) {
    if (Get-Command magick -ErrorAction SilentlyContinue) {
        Write-Host "Generating Windows icon (.ico) from $iconPng..."
        magick "$iconPng" -define icon:auto-resize=16,20,24,32,40,48,64,128,256 "$iconIco"
    } else {
        throw "Missing $iconIco. Install ImageMagick (magick) to auto-generate it, or create the .ico manually."
    }
}

Write-Host "Creating Windows MSI installer..."
jpackage `
  --name "$appName" `
  --input "$stagedInput" `
  --main-jar "hic-studio.jar" `
  --main-class "$mainClass" `
  --type msi `
  --app-version "$AppVersion" `
  --vendor "$vendor" `
  --icon "$iconIco" `
  --win-menu `
  --win-shortcut `
  --win-dir-chooser `
  --dest "$destDir"

Remove-Item -Path $stagedInput -Recurse -Force

Write-Host "Done. Installer created in: $destDir"
