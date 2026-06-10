param(
    [switch]$Clean,
    [switch]$Release,
    [switch]$ChinaMirrors,
    [switch]$Test
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
try {
    $FileSystem = New-Object -ComObject Scripting.FileSystemObject
    $ShortRoot = $FileSystem.GetFolder($Root).ShortPath
    if ($ShortRoot) {
        $Root = $ShortRoot
    }
} catch {
    Write-Warning "Windows short path is unavailable; using the original project path."
}
$Project = Join-Path $Root "android-app"
$DefaultJava = "C:\Program Files (x86)\Android\openjdk\jdk-17.0.14"
$DefaultSdk = "C:\Program Files (x86)\Android\android-sdk"

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = $DefaultJava
}
if (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = $DefaultSdk
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = Join-Path $Root ".tools\gradle-home"

if (-not (Test-Path (Join-Path $env:JAVA_HOME "bin\java.exe"))) {
    throw "JDK 17 was not found. Set JAVA_HOME."
}
if (-not (Test-Path (Join-Path $env:ANDROID_HOME "platforms\android-35\android.jar"))) {
    throw "Android Platform 35 was not found. Set ANDROID_HOME."
}

$Gradle = Get-ChildItem (Join-Path $Root ".tools") -Directory -Filter "gradle-*" -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending |
    ForEach-Object { Join-Path $_.FullName "bin\gradle.bat" } |
    Where-Object { Test-Path $_ } |
    Select-Object -First 1

if (-not $Gradle) {
    $GradleCommand = Get-Command gradle.bat -ErrorAction SilentlyContinue
    if ($GradleCommand) {
        $Gradle = $GradleCommand.Source
    }
}
if (-not $Gradle) {
    throw "Gradle was not found. Extract an official Gradle distribution into .tools."
}

$BuildTask = if ($Release) { "assembleRelease" } else { "assembleDebug" }
$Arguments = @("--no-daemon", "--stacktrace")
if ($ChinaMirrors) {
    $Arguments += "-Pagentpad.useChinaMirrors=true"
    Write-Host "Dependency source: Aliyun Maven mirrors (explicitly enabled)"
} else {
    Write-Host "Dependency source: Google Maven / Maven Central"
}
if ($Clean) {
    $Arguments += "clean"
}
if ($Test) {
    $Arguments += "testDebugUnitTest"
}
$Arguments += $BuildTask

Push-Location $Project
try {
    & $Gradle @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle build failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
