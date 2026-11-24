param([ValidateRange(1024,65535)][int]$Puerto = 18083)
$ErrorActionPreference = 'Stop'
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$registro = Join-Path $raiz "Citas\target\demo-local\proceso-$Puerto.json"
if (-not (Test-Path -LiteralPath $registro)) { throw 'No existe un proceso propio registrado para ese puerto.' }
$datos = Get-Content -LiteralPath $registro -Raw | ConvertFrom-Json
$proceso = Get-Process -Id $datos.pid -ErrorAction SilentlyContinue
if (-not $proceso) { throw 'El proceso registrado ya no existe. No se detuvo ningun proceso.' }
try {
    $jar = Join-Path $raiz 'Citas\target\Citas-0.0.1-SNAPSHOT.jar'
    $linea = (Get-CimInstance Win32_Process -Filter "ProcessId = $($proceso.Id)").CommandLine
    if ($proceso.StartTime.ToUniversalTime().Ticks -ne [long]$datos.inicioTicks -or
            $proceso.Path -ne $datos.ejecutable -or $datos.jar -ne $jar -or
            $datos.puerto -ne $Puerto -or -not $linea.Contains('-jar "' + $jar + '"') -or
            -not $linea.Contains("--server.port=$Puerto ")) {
        throw 'La identidad del proceso no coincide; no se detuvo nada.'
    }
    $datos.paradaSolicitada = $true
    $datos | ConvertTo-Json | Set-Content -LiteralPath $registro
    # Termina exclusivamente la JVM identificada. No borra ni modifica la base.
    $proceso.Kill()
    if (-not $proceso.WaitForExit(10000)) { throw 'La JVM no termino en el plazo esperado.' }
    Write-Output 'JVM propia detenida. La base permanece en su carpeta de datos.'
} finally { $proceso.Dispose() }
