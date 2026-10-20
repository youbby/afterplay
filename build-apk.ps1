param([switch]$Test)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME) {
    $studioJdk = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath $studioJdk) { $env:JAVA_HOME = $studioJdk }
}
if (-not $env:ANDROID_HOME) {
    $sdkPath = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $sdkPath) { $env:ANDROID_HOME = $sdkPath }
}
$tasks = @('assembleDebug')
if ($Test) { $tasks += @('testDebugUnitTest', 'lintDebug') }
$previousErrorPreference = $ErrorActionPreference
try {
    # Windows PowerShell treats native stderr warnings as errors; use the exit code.
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat @tasks --console=plain
} finally { $ErrorActionPreference = $previousErrorPreference }
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
New-Item -ItemType Directory -Path artifacts -Force | Out-Null
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'artifacts\AfterPlay-1.2.1-debug.apk'
Get-FileHash -LiteralPath 'artifacts\AfterPlay-1.2.1-debug.apk' -Algorithm SHA256
Write-Host 'APK: artifacts\AfterPlay-1.2.1-debug.apk'
