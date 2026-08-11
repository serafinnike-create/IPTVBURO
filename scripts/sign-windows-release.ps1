[CmdletBinding()]
param(
    [string]$TimestampUrl = $env:IPTVBURO_TIMESTAMP_URL
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Resolve-SignTool {
    $available = Get-Command signtool.exe -ErrorAction SilentlyContinue
    if ($null -ne $available) { return $available.Source }

    $kitsRoot = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
    $candidate =
        Get-ChildItem -LiteralPath $kitsRoot -Recurse -Filter signtool.exe -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]x64[\\/]signtool[.]exe$' } |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if ($null -eq $candidate) {
        throw 'signtool.exe was not found. Install the Windows SDK Signing Tools first.'
    }
    return $candidate.FullName
}

function Assert-CodeSigningCertificate([string]$Thumbprint) {
    if ($Thumbprint -notmatch '^[A-F0-9]{40}$') {
        throw 'IPTVBURO_WINDOWS_CERT_THUMBPRINT must be a 40-character SHA-1 certificate thumbprint.'
    }
    $certificate = Get-Item -LiteralPath "Cert:\CurrentUser\My\$Thumbprint" -ErrorAction Stop
    if (-not $certificate.HasPrivateKey) { throw 'The selected certificate has no private key.' }
    if ($certificate.NotBefore -gt (Get-Date) -or $certificate.NotAfter -le (Get-Date)) {
        throw 'The selected code-signing certificate is not currently valid.'
    }
    $codeSigningOid = '1.3.6.1.5.5.7.3.3'
    if ($certificate.EnhancedKeyUsageList.ObjectId.Value -notcontains $codeSigningOid) {
        throw 'The selected certificate is not authorized for code signing.'
    }
}

function Invoke-SignAndVerify([string]$SignTool, [string]$Thumbprint, [string]$Url, [string]$Target) {
    & $SignTool sign /fd SHA256 /sha1 $Thumbprint /s My /tr $Url /td SHA256 $Target
    if ($LASTEXITCODE -ne 0) { throw "Signing failed for $Target" }
    & $SignTool verify /pa /all $Target
    if ($LASTEXITCODE -ne 0) { throw "Authenticode verification failed for $Target" }
    $signature = Get-AuthenticodeSignature -LiteralPath $Target
    if ($signature.Status -ne 'Valid') { throw "Windows did not accept the signature on $Target" }
}

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$gradleWrapper = Join-Path $repoRoot 'gradlew.bat'
$thumbprint = (([string]$env:IPTVBURO_WINDOWS_CERT_THUMBPRINT) -replace '\s', '').ToUpperInvariant()
if ([string]::IsNullOrWhiteSpace($TimestampUrl)) {
    throw 'Set IPTVBURO_TIMESTAMP_URL to the HTTPS RFC 3161 service required by the certificate issuer.'
}
$timestampUri = [Uri]$TimestampUrl
if (-not $timestampUri.IsAbsoluteUri -or $timestampUri.Scheme -ne 'https') {
    throw 'IPTVBURO_TIMESTAMP_URL must be an absolute HTTPS URL.'
}

Assert-CodeSigningCertificate $thumbprint
$signTool = Resolve-SignTool

# Gradle signs the freshly generated launcher in packageMsi.doFirst, after every dependency has
# finished but before WiX reads it. The outer script then signs the finished MSI.
$previousPipeline = $env:IPTVBURO_WINDOWS_SIGNING_PIPELINE
$previousSignTool = $env:IPTVBURO_SIGNTOOL
$previousTimestampUrl = $env:IPTVBURO_TIMESTAMP_URL
try {
    $env:IPTVBURO_WINDOWS_SIGNING_PIPELINE = 'true'
    $env:IPTVBURO_SIGNTOOL = $signTool
    $env:IPTVBURO_TIMESTAMP_URL = $TimestampUrl
    & $gradleWrapper ':apps:desktop:packageMsi' '--rerun-tasks' '--no-daemon'
    if ($LASTEXITCODE -ne 0) { throw 'The Windows MSI build failed.' }
}
finally {
    if ($null -eq $previousPipeline) { Remove-Item Env:IPTVBURO_WINDOWS_SIGNING_PIPELINE -ErrorAction SilentlyContinue }
    else { $env:IPTVBURO_WINDOWS_SIGNING_PIPELINE = $previousPipeline }
    if ($null -eq $previousSignTool) { Remove-Item Env:IPTVBURO_SIGNTOOL -ErrorAction SilentlyContinue }
    else { $env:IPTVBURO_SIGNTOOL = $previousSignTool }
    if ($null -eq $previousTimestampUrl) { Remove-Item Env:IPTVBURO_TIMESTAMP_URL -ErrorAction SilentlyContinue }
    else { $env:IPTVBURO_TIMESTAMP_URL = $previousTimestampUrl }
}

$msiDirectory = Join-Path $repoRoot 'apps\desktop\build\compose\binaries\main\msi'
$installer =
    Get-ChildItem -LiteralPath $msiDirectory -Filter '*.msi' -File |
    Sort-Object LastWriteTimeUtc -Descending |
    Select-Object -First 1
if ($null -eq $installer) { throw 'Gradle completed without producing an MSI.' }

Invoke-SignAndVerify $signTool $thumbprint $TimestampUrl $installer.FullName
$hash = (Get-FileHash -LiteralPath $installer.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Signed MSI: $($installer.FullName)"
Write-Host "SHA-256: $hash"
