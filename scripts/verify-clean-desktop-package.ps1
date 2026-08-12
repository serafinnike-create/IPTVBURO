[CmdletBinding()]
param(
    [string]$ArtifactRoot = 'apps\desktop\build\compose\binaries\main\app\IPTVBURO'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$repoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$rootCandidate = if ([IO.Path]::IsPathRooted($ArtifactRoot)) { $ArtifactRoot } else { Join-Path $repoRoot $ArtifactRoot }
$root = (Resolve-Path -LiteralPath $rootCandidate).Path
if (-not $root.StartsWith($repoRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The artifact must be inside the IPTV BURO repository workspace.'
}

function Get-RelativePath([string]$BasePath, [string]$Path) {
    $baseUri = [Uri]($BasePath.TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar)
    $pathUri = [Uri]$Path
    return [Uri]::UnescapeDataString($baseUri.MakeRelativeUri($pathUri).ToString()).Replace('/', '\')
}

$forbiddenExtensions = @('.m3u', '.m3u8', '.db', '.sqlite', '.sqlite3', '.pfx', '.p12', '.jks', '.keystore')
$forbiddenFiles =
    Get-ChildItem -LiteralPath $root -Recurse -File |
    Where-Object { $forbiddenExtensions -contains $_.Extension.ToLowerInvariant() }
if ($forbiddenFiles) {
    $relative = $forbiddenFiles | ForEach-Object { Get-RelativePath $root $_.FullName }
    throw "Private-data-shaped files were found in the package: $($relative -join ', ')"
}

$generatedConfig = Join-Path $repoRoot 'apps\desktop\build\generated\buildconfig\com\lucasserafin94\iptvburo\desktop\build\BuildKeys.kt'
if (-not (Test-Path -LiteralPath $generatedConfig)) {
    throw 'The generated build identity is missing; build the desktop package before auditing it.'
}
$generatedText = Get-Content -LiteralPath $generatedConfig -Raw -Encoding utf8
if ($generatedText -notmatch 'BUNDLED_TMDB_KEY:\s*String\s*=\s*""') {
    throw 'The package build configuration contains a bundled TMDb key.'
}

# Exact values are read locally and never printed. This catches the accidental inclusion that
# prompted this gate without putting the secret itself in a command line, log or report.
$sensitiveValues = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
$profilePath = [Environment]::GetFolderPath('UserProfile')
if (-not [string]::IsNullOrWhiteSpace($profilePath)) { [void]$sensitiveValues.Add($profilePath) }
$localProperties = Join-Path $repoRoot 'local.properties'
if (Test-Path -LiteralPath $localProperties) {
    foreach ($line in Get-Content -LiteralPath $localProperties -Encoding utf8) {
        if ($line -notmatch '^\s*([^#!][^=]*)=(.*)$') { continue }
        $name = $matches[1].Trim()
        $value = $matches[2].Trim()
        if ($name -match '(?i)(api.?key|token|password|secret|username)' -and $value.Length -ge 6) {
            [void]$sensitiveValues.Add($value)
        }
    }
}

function Assert-NoSensitiveText([string]$Text, [string]$Location) {
    foreach ($value in $sensitiveValues) {
        if ($Text.IndexOf($value, [StringComparison]::Ordinal) -ge 0) {
            throw "A private workstation value was found in packaged content: $Location"
        }
    }
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archives = Get-ChildItem -LiteralPath $root -Recurse -File -Filter '*.jar'
foreach ($archive in $archives) {
    $zip = [IO.Compression.ZipFile]::OpenRead($archive.FullName)
    try {
        foreach ($entry in $zip.Entries) {
            if ($entry.Length -eq 0 -or $entry.Length -gt 64MB) { continue }
            $stream = $entry.Open()
            try {
                $memory = [IO.MemoryStream]::new()
                try {
                    $stream.CopyTo($memory)
                    $text = [Text.Encoding]::UTF8.GetString($memory.ToArray())
                    Assert-NoSensitiveText $text "$($archive.Name)!/$($entry.FullName)"
                }
                finally { $memory.Dispose() }
            }
            finally { $stream.Dispose() }
        }
    }
    finally { $zip.Dispose() }
}

$textExtensions = @('.json', '.properties', '.txt', '.cfg', '.ini', '.xml', '.cmd', '.bat', '.ps1')
foreach ($file in Get-ChildItem -LiteralPath $root -Recurse -File) {
    if ($textExtensions -notcontains $file.Extension.ToLowerInvariant() -or $file.Length -gt 64MB) { continue }
    Assert-NoSensitiveText (Get-Content -LiteralPath $file.FullName -Raw -Encoding utf8) (Get-RelativePath $root $file.FullName)
}

$files = @(Get-ChildItem -LiteralPath $root -Recurse -File)
$bytes = ($files | Measure-Object -Property Length -Sum).Sum
Write-Host "Clean desktop package verified: $($files.Count) files, $bytes bytes."
Write-Host 'No playlist/database/key-store files, bundled TMDb key or exact workstation values found.'
