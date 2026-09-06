[CmdletBinding()]
param(
    [string]$TizenStudio = 'C:\tizen-studio',
    [string]$ProfileName = 'burocertificate-emulator',
    [string]$VmName = 'T-samsung-10.0-x86_64',
    [string]$DeviceSerial = 'emulator-26101',
    [ValidateRange(30, 600)][int]$BootTimeoutSeconds = 180,
    [ValidateRange(5, 60)][int]$SmokeTimeoutSeconds = 20,
    [ValidateRange(0, 60)][int]$ObservationSeconds = 5,
    [ValidateRange(0, 65535)][int]$ReportPort = 0,
    [int]$PowerEcpCode = 116,
    [string]$PublicProbeUri = 'iptvburo://title?id=movie%3Auri-probe%3A2099&t=Teste%20de%20link&y=2099',
    [switch]$ColdStart
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ($LASTEXITCODE): $FilePath $($Arguments -join ' ')"
        }
    } finally { Pop-Location }
}

function Assert-PublicProbeUri {
    param([Parameter(Mandatory = $true)][string]$Value)

    if ($Value.Length -gt 2048) { throw 'A URI publica de teste excede 2048 caracteres.' }
    $parsed = $null
    if (-not [Uri]::TryCreate($Value, [UriKind]::Absolute, [ref]$parsed) -or
            $parsed.Scheme -cne 'iptvburo' -or $parsed.Host -cne 'title' -or
            $parsed.UserInfo -or $parsed.Fragment -or $parsed.Port -ne -1 -or
            @('', '/') -notcontains $parsed.AbsolutePath) {
        throw 'A URI de teste deve usar exatamente iptvburo://title e nao pode conter usuario, porta, caminho ou fragmento.'
    }

    $fields = @{}
    foreach ($pair in $parsed.Query.TrimStart('?').Split('&', [StringSplitOptions]::RemoveEmptyEntries)) {
        $parts = $pair.Split('=', 2)
        if ($parts.Count -ne 2) { throw 'A URI de teste contem um parametro sem valor.' }
        $name = [Uri]::UnescapeDataString($parts[0])
        $valueDecoded = [Uri]::UnescapeDataString($parts[1])
        if (@('id', 't', 'y') -notcontains $name) { throw "Parametro publico nao permitido na URI de teste: $name" }
        if ($fields.ContainsKey($name)) { throw "Parametro publico duplicado na URI de teste: $name" }
        $fields[$name] = $valueDecoded
    }
    if (-not $fields.ContainsKey('id') -or -not $fields.ContainsKey('t')) {
        throw 'A URI de teste exige os parametros publicos id e t.'
    }
    if ($fields.id.Length -gt 300 -or $fields.t.Length -gt 300 -or
            $fields.id -notmatch '^(movie|series|episode|live|unknown):[^:]+(?::\d{4})?$' -or
            [string]::IsNullOrWhiteSpace($fields.t) -or $fields.t -match '[\x00-\x1f\x7f]') {
        throw 'A identidade ou o titulo publico da URI de teste e invalido.'
    }
    if ($fields.ContainsKey('y') -and $fields.y -notmatch '^(18(8[8-9]|9\d)|19\d{2}|20\d{2}|2100)$') {
        throw 'O ano publico da URI de teste deve estar entre 1888 e 2100.'
    }
}

function Wait-SmokeResultFile {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$StagePath,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $Path) { return [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8) }
        Start-Sleep -Milliseconds 200
    }
    $stage = if (Test-Path -LiteralPath $StagePath) {
        (([IO.File]::ReadAllLines($StagePath, [Text.Encoding]::UTF8) | Select-Object -Last 1) -replace '[^A-Z0-9_]', '')
    } else { 'SEM_CONTATO_COM_RECEPTOR' }
    throw "O remetente Tizen nao confirmou a entrega em $TimeoutSeconds segundos; ultima etapa segura: $stage."
}

function Wait-ReportServerReady {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][Diagnostics.Process]$Process,
        [ValidateRange(1, 30)][int]$TimeoutSeconds = 10
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        if (Test-Path -LiteralPath $Path) {
            $value = [IO.File]::ReadAllText($Path, [Text.Encoding]::ASCII)
            $activePort = 0
            if ([int]::TryParse($value, [ref]$activePort) -and $activePort -ge 1 -and $activePort -le 65535) {
                return $activePort
            }
            throw 'O receptor local informou uma porta invalida.'
        }
        if ($Process.HasExited) { throw "O receptor local encerrou prematuramente com codigo $($Process.ExitCode)." }
        Start-Sleep -Milliseconds 100
    }
    throw 'O receptor local nao iniciou em 10 segundos.'
}

function Get-OnlineDeviceSerials {
    param([Parameter(Mandatory = $true)][string]$Sdb)

    $serials = @(
        & $Sdb devices |
            ForEach-Object {
                if ($_ -match '^\s*(\S+)\s+device(?:\s|$)') { $Matches[1] }
            }
    )
    if ($LASTEXITCODE -ne 0) { throw "Falha ($LASTEXITCODE) ao consultar dispositivos Tizen." }
    return $serials
}

function Wait-ForOfflineDevice {
    param(
        [Parameter(Mandatory = $true)][string]$Sdb,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (@(Get-OnlineDeviceSerials -Sdb $Sdb) -notcontains $Serial) { return }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "O dispositivo $Serial nao ficou offline em $TimeoutSeconds segundos."
}

function Wait-ForOnlineDevice {
    param(
        [Parameter(Mandatory = $true)][string]$Sdb,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if (@(Get-OnlineDeviceSerials -Sdb $Sdb) -contains $Serial) { return }
        Start-Sleep -Milliseconds 1000
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "O dispositivo $Serial nao ficou online em $TimeoutSeconds segundos."
}

function Restart-EmulatorForColdStart {
    param(
        [Parameter(Mandatory = $true)][string]$Studio,
        [Parameter(Mandatory = $true)][string]$EmCli,
        [Parameter(Mandatory = $true)][string]$EcpCli,
        [Parameter(Mandatory = $true)][string]$Sdb,
        [Parameter(Mandatory = $true)][string]$Vm,
        [Parameter(Mandatory = $true)][string]$Serial,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds,
        [Parameter(Mandatory = $true)][int]$PowerCode
    )

    $studioPrefix = ([IO.Path]::GetFullPath($Studio)).TrimEnd('\') + '\'
    $emulators = @(
        Get-Process -Name 'emulator-x86_64' -ErrorAction SilentlyContinue |
            Where-Object {
                try { $_.Path.StartsWith($studioPrefix, [StringComparison]::OrdinalIgnoreCase) }
                catch { $false }
            }
    )
    if ($emulators.Count -ne 1) {
        throw "A abertura fria exige exatamente um processo emulator-x86_64 dentro de $studioPrefix; encontrados: $($emulators.Count)."
    }
    Write-Host 'Desligando a TV pela tecla Power da skin Samsung para provar a abertura fria...' -ForegroundColor Cyan
    Invoke-Checked -FilePath $EcpCli -Arguments @(
        '--target', $Vm, 'keycode', [string]$PowerCode, '120'
    ) -WorkingDirectory $WorkingDirectory
    Wait-ForOfflineDevice -Sdb $Sdb -Serial $Serial -TimeoutSeconds 30
    if (-not $emulators[0].WaitForExit(60000)) {
        throw 'A VM nao concluiu o desligamento por Power em 60 segundos; nenhum encerramento forcado foi executado.'
    }
    Invoke-Checked -FilePath $EmCli -Arguments @('launch', '-n', $Vm) -WorkingDirectory $WorkingDirectory
    Wait-ForOnlineDevice -Sdb $Sdb -Serial $Serial -TimeoutSeconds $TimeoutSeconds
    Write-Host 'VM reconectada; aguardando os servicos da TV concluirem o boot...' -ForegroundColor Cyan
    Start-Sleep -Seconds 20
}

$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$fixture = Join-Path $repository 'apps\samsung-tizen-tests\fixtures\appcontrol-smoke'
$tizen = Join-Path $TizenStudio 'tools\ide\bin\tizen.bat'
$sdb = Join-Path $TizenStudio 'tools\sdb.exe'
$emCli = Join-Path $TizenStudio 'tools\emulator\bin\em-cli.bat'
$ecpCli = Join-Path $TizenStudio 'tools\emulator\bin\ecp-cli.bat'
$reportServerScript = Join-Path $repository 'scripts\samsung-appcontrol-report-server.js'
$node = (Get-Command 'node.exe' -ErrorAction Stop).Source
foreach ($required in @($fixture, $tizen, $sdb, $emCli, $ecpCli, $reportServerScript, $node)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Caminho necessario nao encontrado: $required" }
}
$online = @(& $sdb devices | Where-Object { $_ -match "^\s*$([Regex]::Escape($DeviceSerial))\s+device(?:\s|$)" })
if ($LASTEXITCODE -ne 0 -or $online.Count -ne 1) { throw "O dispositivo $DeviceSerial nao esta conectado." }
Assert-PublicProbeUri -Value $PublicProbeUri

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$staging = Join-Path $repository "workspace\tizen-appcontrol-smoke-$stamp"
New-Item -ItemType Directory -Path $staging -Force | Out-Null
foreach ($file in @('config.xml', 'index.html', 'smoke.js')) {
    Copy-Item -LiteralPath (Join-Path $fixture $file) -Destination $staging
}
Copy-Item -LiteralPath (Join-Path $repository 'apps\samsung-tizen\.project') -Destination $staging
Copy-Item -LiteralPath (Join-Path $repository 'apps\samsung-tizen\.tproject') -Destination $staging

$reportResultFile = Join-Path $staging 'appcontrol-result.txt'
$reportReadyFile = Join-Path $staging 'appcontrol-ready.txt'
$reportStageFile = Join-Path $staging 'appcontrol-stage.txt'
$shutdownNonce = [Guid]::NewGuid().ToString()
$reportProcess = $null
$installed = $false
try {
    $reportLifetimeMs = [Math]::Max(120000, ($BootTimeoutSeconds + $SmokeTimeoutSeconds + 120) * 1000)
    $reportProcess = Start-Process -FilePath $node -ArgumentList @(
        '"' + $reportServerScript + '"', [string]$ReportPort,
        '"' + $reportResultFile + '"', '"' + $reportReadyFile + '"', '"' + $reportStageFile + '"',
        $shutdownNonce, [string]$reportLifetimeMs
    ) -WindowStyle Hidden -PassThru
    $activeReportPort = Wait-ReportServerReady -Path $reportReadyFile -Process $reportProcess
    $configPath = Join-Path $staging 'config.xml'
    $configSource = [IO.File]::ReadAllText($configPath, [Text.Encoding]::UTF8)
    $configSource = [Regex]::Replace(
        $configSource,
        '<access origin="http://[^"/]+:\d+" subdomains="false"/>',
        "<access origin=`"http://10.0.2.2:$activeReportPort`" subdomains=`"false`"/>",
        [Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    [IO.File]::WriteAllText($configPath, $configSource, [Text.UTF8Encoding]::new($false))
    $smokePath = Join-Path $staging 'smoke.js'
    $smokeSource = [IO.File]::ReadAllText($smokePath, [Text.Encoding]::UTF8)
    $safeProbeLiteral = ConvertTo-Json -Compress $PublicProbeUri
    $smokeSource = [Regex]::Replace(
        $smokeSource,
        "var REPORT_BASE = 'http://10\.0\.2\.2:\d+/\?result=';",
        "var REPORT_BASE = 'http://10.0.2.2:$activeReportPort/?result=';",
        [Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    $smokeSource = [Regex]::Replace(
        $smokeSource,
        "var PUBLIC_PROBE_URI = '[^']+';",
        "var PUBLIC_PROBE_URI = $safeProbeLiteral;",
        [Text.RegularExpressions.RegexOptions]::CultureInvariant
    )
    [IO.File]::WriteAllText($smokePath, $smokeSource, [Text.UTF8Encoding]::new($false))
    Invoke-Checked -FilePath $tizen -Arguments @('build-web', '--', '.') -WorkingDirectory $staging
    Invoke-Checked -FilePath $tizen -Arguments @('package', '-t', 'wgt', '-s', $ProfileName, '--', '.buildResult') -WorkingDirectory $staging
    $buildResult = Join-Path $staging '.buildResult'
    $signedPackage = Get-ChildItem -LiteralPath $buildResult -Filter '*.wgt' | Select-Object -First 1
    if (-not $signedPackage) { throw 'O pacote de smoke assinado nao foi criado.' }
    $installPackage = Join-Path $buildResult 'IPTVBURO-AppControl-Smoke.wgt'
    Copy-Item -LiteralPath $signedPackage.FullName -Destination $installPackage
    Invoke-Checked -FilePath $tizen -Arguments @('install-permit', '-s', $DeviceSerial) -WorkingDirectory $staging
    Invoke-Checked -FilePath $tizen -Arguments @('install', '-n', 'IPTVBURO-AppControl-Smoke.wgt', '-s', $DeviceSerial, '--', '.buildResult') -WorkingDirectory $staging
    $installed = $true
    if ($ColdStart) {
        Restart-EmulatorForColdStart -Studio $TizenStudio -EmCli $emCli -EcpCli $ecpCli -Sdb $sdb `
            -Vm $VmName -Serial $DeviceSerial -WorkingDirectory $repository `
            -TimeoutSeconds $BootTimeoutSeconds -PowerCode $PowerEcpCode
    }
    Invoke-Checked -FilePath $tizen -Arguments @('run', '-p', 'BUROCTL001.AppControlSmoke', '-s', $DeviceSerial) -WorkingDirectory $staging
    $result = Wait-SmokeResultFile -Path $reportResultFile -StagePath $reportStageFile -TimeoutSeconds $SmokeTimeoutSeconds
    if ($result -ne 'APPCONTROL_LAUNCH_PASS') { throw "O Tizen recusou a entrega: $result" }
    Write-Host ''
    Write-Host 'APPCONTROL_SMOKE_PASS' -ForegroundColor Green
    Write-Host "Dispositivo: $DeviceSerial"
    Write-Host "Abertura: $(if ($ColdStart) { 'fria' } else { 'quente' })"
    Write-Host 'URI: iptvburo://title (somente identidade publica validada; valor omitido)'
    Write-Host "Pacote temporario: $installPackage"
    if ($ObservationSeconds -gt 0) {
        Write-Host "Mantendo o remetente instalado por $ObservationSeconds segundos para o consumidor processar o pedido."
        Start-Sleep -Seconds $ObservationSeconds
    }
} finally {
    if ($reportProcess -and -not $reportProcess.HasExited -and (Test-Path -LiteralPath $reportReadyFile)) {
        try {
            $shutdownPort = [IO.File]::ReadAllText($reportReadyFile, [Text.Encoding]::ASCII)
            Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$shutdownPort/shutdown?nonce=$shutdownNonce" `
                -TimeoutSec 2 | Out-Null
            [void]$reportProcess.WaitForExit(3000)
        } catch { Write-Warning 'O receptor local temporario encerrara sozinho por timeout.' }
    }
    if ($installed) {
        Invoke-Checked -FilePath $tizen -Arguments @('uninstall', '-p', 'BUROCTL001.AppControlSmoke', '-t', $VmName) -WorkingDirectory $staging
    }
}
