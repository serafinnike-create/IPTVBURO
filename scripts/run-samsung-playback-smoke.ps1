[CmdletBinding()]
param(
    [string]$TizenStudio = 'C:\tizen-studio',
    [string]$ProfileName = 'burocertificate-emulator',
    [string]$VmName = 'T-samsung-10.0-x86_64',
    [ValidateRange(30, 600)][int]$BootTimeoutSeconds = 180,
    [ValidateRange(15, 180)][int]$SmokeTimeoutSeconds = 75,
    [int]$PlayPauseEcpCode = 244,
    [int]$RightEcpCode = 106,
    [int]$LeftEcpCode = 105,
    [int]$FastForwardEcpCode = 208,
    [int]$RewindEcpCode = 168,
    [string]$DeviceSerial
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
        if ($LASTEXITCODE -ne 0) { throw "Falha ($LASTEXITCODE): $FilePath $($Arguments -join ' ')" }
    } finally { Pop-Location }
}

function Get-OnlineDeviceSerials {
    param([Parameter(Mandatory = $true)][string]$Sdb)
    $serials = @(& $Sdb devices | ForEach-Object {
        if ($_ -match '^\s*(\S+)\s+device(?:\s|$)') { $Matches[1] }
    })
    if ($LASTEXITCODE -ne 0) { throw "Falha ($LASTEXITCODE) ao consultar dispositivos Tizen." }
    return $serials
}

function Wait-ForOnlineDevice {
    param(
        [Parameter(Mandatory = $true)][string]$Sdb,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $online = @(Get-OnlineDeviceSerials -Sdb $Sdb)
        if ($online.Count -eq 1) { return [string]$online[0] }
        if ($online.Count -gt 1) { throw "Há mais de um dispositivo conectado. Informe -DeviceSerial." }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "O emulador não ficou online em $TimeoutSeconds segundos."
}

function Send-HttpResponse {
    param([Parameter(Mandatory = $true)]$Client)
    $body = [Text.Encoding]::UTF8.GetBytes('ok')
    $headers = [Text.Encoding]::ASCII.GetBytes("HTTP/1.1 200 OK`r`nContent-Type: text/plain`r`nContent-Length: $($body.Length)`r`nConnection: close`r`n`r`n")
    $stream = $Client.GetStream()
    $stream.Write($headers, 0, $headers.Length)
    $stream.Write($body, 0, $body.Length)
    $stream.Flush()
}

function Receive-AvPlaySmokeResult {
    param(
        [Parameter(Mandatory = $true)]$Listener,
        [Parameter(Mandatory = $true)][int]$SmokeTimeoutSeconds,
        [Parameter(Mandatory = $true)][string]$EcpCli,
        [Parameter(Mandatory = $true)][string]$VmName,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][int]$PlayPauseEcpCode,
        [Parameter(Mandatory = $true)][int]$RightEcpCode,
        [Parameter(Mandatory = $true)][int]$LeftEcpCode,
        [Parameter(Mandatory = $true)][int]$FastForwardEcpCode,
        [Parameter(Mandatory = $true)][int]$RewindEcpCode
    )
    $deadline = [DateTime]::UtcNow.AddSeconds($SmokeTimeoutSeconds)
    $lastReport = 'nenhum callback recebido'
    $pauseSent = $false
    $resumeSent = $false
    $seekForwardSent = $false
    $seekBackSent = $false
    $fastForwardSent = $false
    $rewindSent = $false
    $acceptTask = $Listener.AcceptTcpClientAsync()
    do {
        if (-not $acceptTask.Wait(250)) { continue }
        $client = $acceptTask.Result
        try {
            $reader = New-Object IO.StreamReader($client.GetStream(), [Text.Encoding]::ASCII, $false, 1024, $true)
            $requestLine = $reader.ReadLine()
            while ($reader.ReadLine()) { }
            Send-HttpResponse -Client $client
            if ($requestLine -match '^GET\s+(\S+)\s+HTTP/') {
                $requestTarget = [Uri]::UnescapeDataString($Matches[1])
                if ($requestTarget.StartsWith('/iptvburo.avplay-smoke?')) {
                    $lastReport = $requestTarget
                    Write-Host "Callback da VM: $lastReport" -ForegroundColor DarkGray
                    if ($requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_PASS(?:&|$)') { return $lastReport }
                    if ($requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_FAIL(?:&|$)') {
                        throw "AVPlay informou falha: $lastReport"
                    }
                    if (-not $pauseSent -and $requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_READY(?:&|$)') {
                        $pauseSent = $true
                        Invoke-Checked -FilePath $EcpCli -Arguments @('--target', $VmName, 'keycode', [string]$PlayPauseEcpCode, '120') -WorkingDirectory $WorkingDirectory
                    } elseif (-not $resumeSent -and $requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_PAUSED(?:&|$)') {
                        $resumeSent = $true
                        Invoke-Checked -FilePath $EcpCli -Arguments @('--target', $VmName, 'keycode', [string]$PlayPauseEcpCode, '120') -WorkingDirectory $WorkingDirectory
                    } elseif (-not $seekForwardSent -and $requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_RESUMED(?:&|$)') {
                        $seekForwardSent = $true
                        Invoke-Checked -FilePath $EcpCli -Arguments @('--target', $VmName, 'keycode', [string]$RightEcpCode, '120') -WorkingDirectory $WorkingDirectory
                    } elseif (-not $seekBackSent -and $requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_SEEK_FORWARD(?:&|$)') {
                        $seekBackSent = $true
                        Invoke-Checked -FilePath $EcpCli -Arguments @('--target', $VmName, 'keycode', [string]$LeftEcpCode, '120') -WorkingDirectory $WorkingDirectory
                    } elseif (-not $fastForwardSent -and $requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_SEEK_BACK(?:&|$)') {
                        $fastForwardSent = $true
                        Invoke-Checked -FilePath $EcpCli -Arguments @('--target', $VmName, 'keycode', [string]$FastForwardEcpCode, '120') -WorkingDirectory $WorkingDirectory
                    } elseif (-not $rewindSent -and $requestTarget -match '(?:\?|&)result=AVPLAY_SMOKE_FAST_FORWARD(?:&|$)') {
                        $rewindSent = $true
                        Invoke-Checked -FilePath $EcpCli -Arguments @('--target', $VmName, 'keycode', [string]$RewindEcpCode, '120') -WorkingDirectory $WorkingDirectory
                    }
                }
            }
        } finally { $client.Dispose() }
        $acceptTask = $Listener.AcceptTcpClientAsync()
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "AVPlay não comprovou reprodução em $SmokeTimeoutSeconds segundos. Último resultado: $lastReport"
}

$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$fixture = Join-Path $repository 'apps\samsung-tizen-tests\fixtures\playback-smoke'
$productionPlayer = Join-Path $repository 'apps\samsung-tizen\js\player.js'
$productionKeys = Join-Path $repository 'apps\samsung-tizen\js\keys.js'
$tizen = Join-Path $TizenStudio 'tools\ide\bin\tizen.bat'
$sdb = Join-Path $TizenStudio 'tools\sdb.exe'
$emCli = Join-Path $TizenStudio 'tools\emulator\bin\em-cli.bat'
$ecpCli = Join-Path $TizenStudio 'tools\emulator\bin\ecp-cli.bat'

foreach ($required in @($fixture, $productionPlayer, $productionKeys, $tizen, $sdb, $emCli, $ecpCli)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Caminho necessário não encontrado: $required" }
}

$online = @(Get-OnlineDeviceSerials -Sdb $sdb)
if ($DeviceSerial) {
    if ($online -notcontains $DeviceSerial) { throw "O dispositivo $DeviceSerial não está conectado." }
} elseif ($online.Count -eq 0) {
    Invoke-Checked -FilePath $emCli -Arguments @('launch', '-n', $VmName) -WorkingDirectory $repository
    $DeviceSerial = Wait-ForOnlineDevice -Sdb $sdb -TimeoutSeconds $BootTimeoutSeconds
    Start-Sleep -Seconds 20
} elseif ($online.Count -eq 1) { $DeviceSerial = [string]$online[0] }
else { throw "Há mais de um dispositivo conectado. Informe -DeviceSerial." }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$staging = Join-Path $repository "workspace\tizen-avplay-smoke-$stamp"
New-Item -ItemType Directory -Path (Join-Path $staging 'js') -Force | Out-Null
foreach ($file in @('config.xml', 'index.html', 'smoke.js')) {
    Copy-Item -LiteralPath (Join-Path $fixture $file) -Destination $staging
}
Copy-Item -LiteralPath $productionPlayer -Destination (Join-Path $staging 'js\player.js')
Copy-Item -LiteralPath $productionKeys -Destination (Join-Path $staging 'js\keys.js')
Copy-Item -LiteralPath (Join-Path $repository 'apps\samsung-tizen\.project') -Destination $staging
Copy-Item -LiteralPath (Join-Path $repository 'apps\samsung-tizen\.tproject') -Destination $staging

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Any, 43127)
$listener.Start()
$smokePackageInstalled = $false
try {
    Invoke-Checked -FilePath $tizen -Arguments @('build-web', '--', '.') -WorkingDirectory $staging
    Invoke-Checked -FilePath $tizen -Arguments @('package', '-t', 'wgt', '-s', $ProfileName, '--', '.buildResult') -WorkingDirectory $staging
    $buildResult = Join-Path $staging '.buildResult'
    $signedPackage = Get-ChildItem -LiteralPath $buildResult -Filter '*.wgt' | Select-Object -First 1
    if (-not $signedPackage) { throw 'O pacote de smoke assinado não foi criado.' }
    $installPackage = Join-Path $buildResult 'IPTVBURO-AVPlay-Smoke.wgt'
    Copy-Item -LiteralPath $signedPackage.FullName -Destination $installPackage
    Invoke-Checked -FilePath $tizen -Arguments @('install-permit', '-s', $DeviceSerial) -WorkingDirectory $staging
    Invoke-Checked -FilePath $tizen -Arguments @('install', '-n', 'IPTVBURO-AVPlay-Smoke.wgt', '-s', $DeviceSerial, '--', '.buildResult') -WorkingDirectory $staging
    $smokePackageInstalled = $true
    Invoke-Checked -FilePath $tizen -Arguments @('run', '-p', 'BUROSMK001.PlaybackSmoke', '-s', $DeviceSerial) -WorkingDirectory $staging
    $result = Receive-AvPlaySmokeResult -Listener $listener -SmokeTimeoutSeconds $SmokeTimeoutSeconds `
        -EcpCli $ecpCli -VmName $VmName -WorkingDirectory $staging `
        -PlayPauseEcpCode $PlayPauseEcpCode -RightEcpCode $RightEcpCode `
        -LeftEcpCode $LeftEcpCode -FastForwardEcpCode $FastForwardEcpCode -RewindEcpCode $RewindEcpCode
    Write-Host ''
    Write-Host 'AVPLAY_SMOKE_PASS' -ForegroundColor Green
    Write-Host "Dispositivo: $DeviceSerial"
    Write-Host "Pacote: $installPackage"
    Write-Host "Resultado: $result"
} finally {
    $listener.Stop()
    if ($smokePackageInstalled) {
        Invoke-Checked -FilePath $tizen -Arguments @('uninstall', '-p', 'BUROSMK001.PlaybackSmoke', '-t', $VmName) -WorkingDirectory $staging
    }
}
