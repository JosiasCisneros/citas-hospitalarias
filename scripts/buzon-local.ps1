param(
    [ValidateSet('iniciar', 'detener')][string]$Accion = 'iniciar',
    [ValidateRange(1024,65535)][int]$Smtp = 1025,
    [ValidateRange(1024,65535)][int]$Web = 8025
)
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$carpeta = Join-Path $raiz 'Citas\target\mailpit-02A'
$exe = Join-Path $carpeta 'mailpit.exe'
$metadata = Join-Path $carpeta ("proceso-$Smtp-$Web.json")
$hashZip = '0bcf8f1e7c21005b30c206064ff2f37dac602ef66774e558379d326f5673d652'
$hashExe = '0e3184aaa2ac102f016c1872fb79a77a15b310fc4a22eec48c5ddc48d8cea461'
if ($Accion -eq 'detener') {
    if (-not (Test-Path -LiteralPath $metadata)) { throw 'No hay proceso propio registrado para esos puertos.' }
    $info = Get-Content -LiteralPath $metadata -Raw | ConvertFrom-Json
    $proceso = Get-CimInstance Win32_Process -Filter ("ProcessId = " + [int]$info.pid)
    if (-not $proceso) { Write-Output 'El buzon ya estaba detenido.'; return }
    if ($proceso.ExecutablePath -ne $exe -or -not $proceso.CommandLine.Contains("--smtp 127.0.0.1:$Smtp") -or -not $proceso.CommandLine.Contains("--listen 127.0.0.1:$Web")) {
        throw 'El PID no corresponde al buzon propio. No se detuvo ningun proceso.'
    }
    Stop-Process -Id $proceso.ProcessId -ErrorAction Stop
    Write-Output 'Buzon local propio detenido. La base de mensajes permanece en target, excluida de Git.'
    return
}
if ($Smtp -eq $Web) { throw 'SMTP y web requieren puertos distintos.' }
if (Test-Path -LiteralPath $metadata) {
    $anterior = Get-Content -LiteralPath $metadata -Raw | ConvertFrom-Json
    if (Get-Process -Id ([int]$anterior.pid) -ErrorAction SilentlyContinue) { throw 'Hay un proceso registrado; comprobarlo antes de iniciar otro.' }
}
New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
if (-not (Test-Path -LiteralPath $exe)) {
    $zip = Join-Path $carpeta 'mailpit.zip'
    Invoke-WebRequest -Uri 'https://github.com/axllent/mailpit/releases/download/v1.33.1/mailpit-windows-amd64.zip' -OutFile $zip
    if ((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hashZip) { throw 'ZIP de Mailpit no verificado.' }
    Expand-Archive -LiteralPath $zip -DestinationPath $carpeta -Force
}
if ((Get-FileHash -LiteralPath $exe -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hashExe) { throw 'Binario de Mailpit no verificado.' }
# Evitar heredar configuración de relay, credenciales o interfaces de otras instancias.
$inicio = New-Object System.Diagnostics.ProcessStartInfo
$inicio.FileName = $exe
$inicio.WorkingDirectory = $carpeta
$inicio.UseShellExecute = $false
$inicio.CreateNoWindow = $true
foreach ($clave in @($inicio.EnvironmentVariables.Keys)) {
    if ($clave -like 'MP_*') { $inicio.EnvironmentVariables.Remove($clave) }
}
$inicio.Arguments = "--smtp 127.0.0.1:$Smtp --listen 127.0.0.1:$Web --allowed-hosts 127.0.0.1,localhost --database mensajes-$Smtp-$Web.sqlite --disable-version-check --quiet --smtp-disable-rdns"
$proceso = [System.Diagnostics.Process]::Start($inicio)
@{pid=$proceso.Id;ejecutable=$exe;smtp=$Smtp;web=$Web;version='1.33.1'} | ConvertTo-Json | Set-Content -LiteralPath $metadata
Write-Output "Mailpit iniciado, PID $($proceso.Id). SMTP 127.0.0.1:$Smtp; web http://127.0.0.1:$Web. Sin relay."
