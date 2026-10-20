param([switch]$Test, [switch]$Release)
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
$signing = $null
if ($Release) {
    $signingFile = Join-Path $PSScriptRoot '.signing/release.clixml'
    if (-not (Test-Path -LiteralPath $signingFile)) {
        throw 'Configure signing first: .\configure-signing.ps1 -KeystorePath <existing.jks> -KeyAlias <alias>'
    }
    $signing = Import-Clixml -LiteralPath $signingFile
    if (-not (Test-Path -LiteralPath $signing.KeystorePath) -or
        $signing.StorePassword -isnot [Security.SecureString] -or
        $signing.KeyPassword -isnot [Security.SecureString]) {
        throw 'Release signing configuration is invalid. Run configure-signing.ps1 again.'
    }
}
$variant = if ($Release) { 'release' } else { 'debug' }
[string[]]$tasks = if ($Release) { @('assembleRelease') } else { @('assembleDebug') }
if ($Test) {
    $tasks += if ($Release) { @('testReleaseUnitTest', 'lintRelease') } else { @('testDebugUnitTest', 'lintDebug') }
}
$previousErrorPreference = $ErrorActionPreference
try {
    # Windows PowerShell treats native stderr warnings as errors; use the exit code.
    $ErrorActionPreference = 'Continue'
    & .\gradlew.bat @tasks --console=plain
} finally { $ErrorActionPreference = $previousErrorPreference }
if ($LASTEXITCODE -ne 0) { throw 'Android build failed.' }
$outputDirectory = Join-Path $PSScriptRoot "app/build/outputs/apk/$variant"
$metadata = Get-Content -Raw -LiteralPath (Join-Path $outputDirectory 'output-metadata.json') | ConvertFrom-Json
$apk = Join-Path $outputDirectory $metadata.elements[0].outputFile
if ($Release) {
    $buildTools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory |
        Where-Object { (Test-Path -LiteralPath (Join-Path $_.FullName 'apksigner.bat')) -and $_.Name -match '^\d+\.\d+\.\d+$' } |
        Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
    if (-not $buildTools) { throw 'Android SDK Build Tools with apksigner are required.' }
    $alignedApk = Join-Path $outputDirectory 'afterplay-aligned.apk'
    $signedApk = Join-Path $outputDirectory 'afterplay-signed.apk'
    & (Join-Path $buildTools.FullName 'zipalign.exe') -f -P 16 4 $apk $alignedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK alignment failed.' }
    $oldStorePassword = [Environment]::GetEnvironmentVariable('AFTERPLAY_STORE_PASSWORD', 'Process')
    $oldKeyPassword = [Environment]::GetEnvironmentVariable('AFTERPLAY_KEY_PASSWORD', 'Process')
    try {
        $env:AFTERPLAY_STORE_PASSWORD = [Net.NetworkCredential]::new('', $signing.StorePassword).Password
        $env:AFTERPLAY_KEY_PASSWORD = [Net.NetworkCredential]::new('', $signing.KeyPassword).Password
        & (Join-Path $buildTools.FullName 'apksigner.bat') sign --ks $signing.KeystorePath --ks-key-alias $signing.KeyAlias `
            --ks-pass env:AFTERPLAY_STORE_PASSWORD --key-pass env:AFTERPLAY_KEY_PASSWORD --out $signedApk $alignedApk
        if ($LASTEXITCODE -ne 0) { throw 'APK signing failed. Check your saved passwords and key alias.' }
    } finally {
        [Environment]::SetEnvironmentVariable('AFTERPLAY_STORE_PASSWORD', $oldStorePassword, 'Process')
        [Environment]::SetEnvironmentVariable('AFTERPLAY_KEY_PASSWORD', $oldKeyPassword, 'Process')
    }
    $verification = & (Join-Path $buildTools.FullName 'apksigner.bat') verify --print-certs $signedApk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    if (-not ($verification -match "certificate SHA-256 digest: $($signing.ExpectedSignerSha256)$")) {
        throw 'The APK signing certificate differs from the published release. No distribution APK was copied.'
    }
    $apk = $signedApk
    Write-Host 'Signature verified: same signing certificate as the published release.'
}
New-Item -ItemType Directory -Path artifacts -Force | Out-Null
$destination = Join-Path $PSScriptRoot "artifacts/AfterPlay-$($metadata.elements[0].versionName)-$variant.apk"
Copy-Item -LiteralPath $apk -Destination $destination
Get-FileHash -LiteralPath $destination -Algorithm SHA256
Write-Host "APK: $destination"
