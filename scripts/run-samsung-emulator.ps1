[CmdletBinding()]
param(
    [string]$TizenStudio = 'C:\tizen-studio',
    [string]$ProfileName = 'burocertificate-emulator',
    [string]$VmName = 'T-samsung-10.0-x86_64',
    [ValidateRange(30, 600)][int]$BootTimeoutSeconds = 180,
    [ValidateRange(3, 60)][int]$LaunchStabilitySeconds = 5,
    [string]$DeviceSerial,
    [switch]$SkipTests
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
    } finally {
        Pop-Location
    }
}

function Invoke-CheckedCapture {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory
    )

    Push-Location $WorkingDirectory
    try {
        $lines = @(& $FilePath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
        $lines | ForEach-Object { Write-Host $_ }
        if ($exitCode -ne 0) {
            throw "Falha ($exitCode): $FilePath $($Arguments -join ' ')"
        }
        return @($lines | ForEach-Object { [string]$_ })
    } finally {
        Pop-Location
    }
}

function Get-TizenLaunchPid {
    param([Parameter(Mandatory = $true)][string[]]$Output)

    $text = $Output -join "`n"
    if ($text -notmatch 'successfully launched pid\s*=\s*(\d+)\s+with debug') {
        throw 'O Tizen nao informou o PID depois de abrir o aplicativo.'
    }
    return [int]$Matches[1]
}

function Get-OnlineDeviceSerials {
    param([Parameter(Mandatory = $true)][string]$Sdb)

    $serials = @(
        & $Sdb devices |
            ForEach-Object {
                if ($_ -match '^\s*(\S+)\s+device(?:\s|$)') { $Matches[1] }
            }
    )
    if ($LASTEXITCODE -ne 0) {
        throw "Falha ($LASTEXITCODE) ao consultar dispositivos Tizen."
    }
    return $serials
}

function Wait-ForOnlineDevice {
    param(
        [Parameter(Mandatory = $true)][string]$Sdb,
        [string]$PreferredSerial,
        [Parameter(Mandatory = $true)][int]$TimeoutSeconds
    )

    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        $online = @(Get-OnlineDeviceSerials -Sdb $Sdb)
        if ($PreferredSerial) {
            if ($online -contains $PreferredSerial) { return $PreferredSerial }
        } elseif ($online.Count -eq 1) {
            return [string]$online[0]
        } elseif ($online.Count -gt 1) {
            throw "Há mais de um dispositivo conectado. Informe -DeviceSerial. Encontrados: $($online -join ', ')"
        }
        Start-Sleep -Milliseconds 1000
    } while ([DateTime]::UtcNow -lt $deadline)

    if ($PreferredSerial) {
        throw "O dispositivo $PreferredSerial não ficou online em $TimeoutSeconds segundos."
    }
    throw "O emulador $VmName não ficou online em $TimeoutSeconds segundos."
}

$repository = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$application = Join-Path $repository 'apps\samsung-tizen'
$tests = Join-Path $repository 'apps\samsung-tizen-tests'
$tizen = Join-Path $TizenStudio 'tools\ide\bin\tizen.bat'
$sdb = Join-Path $TizenStudio 'tools\sdb.exe'
$emCli = Join-Path $TizenStudio 'tools\emulator\bin\em-cli.bat'

foreach ($required in @($application, $tests, $tizen, $sdb, $emCli)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Caminho necessário não encontrado: $required"
    }
}

if (-not $SkipTests) {
    $npm = (Get-Command npm.cmd -ErrorAction Stop).Source
    Invoke-Checked -FilePath $npm -Arguments @('test') -WorkingDirectory $tests
}

$online = @(Get-OnlineDeviceSerials -Sdb $sdb)
$launchedVm = $false
if ($DeviceSerial) {
    if ($online -notcontains $DeviceSerial) {
        throw "O dispositivo $DeviceSerial não está conectado."
    }
} elseif ($online.Count -eq 0) {
    Write-Host "Nenhum alvo online. Iniciando a VM Samsung $VmName..." -ForegroundColor Cyan
    Invoke-Checked -FilePath $emCli -Arguments @('launch', '-n', $VmName) -WorkingDirectory $repository
    $launchedVm = $true
    $DeviceSerial = Wait-ForOnlineDevice -Sdb $sdb -TimeoutSeconds $BootTimeoutSeconds
} elseif ($online.Count -gt 1) {
    throw "Há mais de um dispositivo conectado. Informe -DeviceSerial. Encontrados: $($online -join ', ')"
} else {
    $DeviceSerial = [string]$online[0]
}

if ($launchedVm) {
    Write-Host 'Emulador conectado. Aguardando os serviços da TV concluírem o boot...' -ForegroundColor Cyan
    Start-Sleep -Seconds 20
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$staging = Join-Path $repository "workspace\tizen-emulator-$stamp"
New-Item -ItemType Directory -Path $staging | Out-Null

# Lista fechada: nenhum log, teste, segredo ou .buildResult antigo entra no WGT.
foreach ($file in @('.project', '.tproject', 'config.xml', 'index.html', 'icon.png')) {
    Copy-Item -LiteralPath (Join-Path $application $file) -Destination $staging
}
foreach ($directory in @('assets', 'css', 'js')) {
    Copy-Item -LiteralPath (Join-Path $application $directory) -Destination $staging -Recurse
}

Invoke-Checked -FilePath $tizen -Arguments @('build-web', '--', '.') -WorkingDirectory $staging
$buildResult = Join-Path $staging '.buildResult'
Invoke-Checked -FilePath $tizen -Arguments @('package', '-t', 'wgt', '-s', $ProfileName, '--', '.buildResult') -WorkingDirectory $staging

$signedPackage = Join-Path $buildResult 'IPTV BURO.wgt'
$installPackage = Join-Path $buildResult 'IPTVBURO.wgt'
if (-not (Test-Path -LiteralPath $signedPackage)) {
    throw "O pacote assinado não foi criado: $signedPackage"
}
Copy-Item -LiteralPath $signedPackage -Destination $installPackage

Invoke-Checked -FilePath $tizen -Arguments @('install-permit', '-s', $DeviceSerial) -WorkingDirectory $staging
Invoke-Checked -FilePath $tizen -Arguments @('install', '-n', 'IPTVBURO.wgt', '-s', $DeviceSerial, '--', '.buildResult') -WorkingDirectory $staging
$launchOutput = @(Invoke-CheckedCapture -FilePath $tizen -Arguments @(
    'run', '-p', 'IPTVBUROxx.IPTVBURO', '-s', $DeviceSerial
) -WorkingDirectory $staging)
$launchPid = Get-TizenLaunchPid -Output $launchOutput

# O perfil TV 10 bloqueia shell e dlog, mas o launcher devolve o mesmo PID ao
# ativar uma instância que continua viva. Um PID diferente provaria que o app
# caiu e foi recriado durante a janela; nesse caso o runner deve falhar.
Write-Host "Confirmando estabilidade do processo por $LaunchStabilitySeconds segundos..." -ForegroundColor Cyan
Start-Sleep -Seconds $LaunchStabilitySeconds
$verificationOutput = @(Invoke-CheckedCapture -FilePath $tizen -Arguments @(
    'run', '-p', 'IPTVBUROxx.IPTVBURO', '-s', $DeviceSerial
) -WorkingDirectory $staging)
$verificationPid = Get-TizenLaunchPid -Output $verificationOutput
if ($verificationPid -ne $launchPid) {
    throw "O aplicativo reiniciou durante a validacao: PID inicial $launchPid, PID atual $verificationPid."
}

Write-Host ''
Write-Host 'Samsung Tizen compilado, assinado, instalado e estavel no emulador.' -ForegroundColor Green
Write-Host "Dispositivo: $DeviceSerial"
Write-Host "PID estavel: $launchPid"
Write-Host "Pacote: $installPackage"
