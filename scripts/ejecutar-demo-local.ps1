param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(1024,65535)][int]$Puerto = 18083,
    [string]$DirectorioDatos = $env:CITAS_DATOS,
    [switch]$SinAdministrador,
    [switch]$RecuperacionLocal,
    [ValidateRange(1024,65535)][int]$Smtp = 1025
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Indicar -JavaHome con JDK 26.' }
$raiz = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$jar = Join-Path $raiz 'Citas\target\Citas-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Ejecutar scripts/verificar.ps1 antes de iniciar.' }
if ([string]::IsNullOrWhiteSpace($DirectorioDatos)) { $DirectorioDatos = 'datos-locales\demo' }
if ($DirectorioDatos.Contains(';')) { throw 'La carpeta de datos no puede contener punto y coma.' }
# Una ruta relativa SIEMPRE parte de esta raiz, nunca de la carpeta de la terminal.
if (-not [IO.Path]::IsPathRooted($DirectorioDatos)) { $DirectorioDatos = Join-Path $raiz $DirectorioDatos }
$DirectorioDatos = [IO.Path]::GetFullPath($DirectorioDatos)
$target = [IO.Path]::GetFullPath((Join-Path $raiz 'Citas\target'))
if ($DirectorioDatos -eq $target -or $DirectorioDatos.StartsWith($target + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
    throw 'Los datos persistentes deben quedar fuera de Citas/target para sobrevivir a clean.'
}
New-Item -ItemType Directory -Path $DirectorioDatos -Force | Out-Null
$DirectorioDatos = (Resolve-Path -LiteralPath $DirectorioDatos).Path
$carpeta = Join-Path $raiz 'Citas\target\demo-local'
New-Item -ItemType Directory -Path $carpeta -Force | Out-Null
$registro = Join-Path $carpeta ("proceso-$Puerto.json")
if (Test-Path -LiteralPath $registro) {
    $previo = Get-Content -LiteralPath $registro -Raw | ConvertFrom-Json
    if (Get-Process -Id $previo.pid -ErrorAction SilentlyContinue) { throw 'Ya existe un registro activo para este puerto. Revisar antes de iniciar otra demo.' }
    Remove-Item -LiteralPath $registro
}
Write-Output "Base H2 local: $(Join-Path $DirectorioDatos 'hospital.mv.db'). Una instancia; no se borran datos."
$secreto = $env:DEMO_ADMIN_PASSWORD
$seguro = $null
$proceso = $null
try {
    if (-not $SinAdministrador -and [string]::IsNullOrEmpty($secreto)) {
        $seguro = Read-Host 'Contrasena de alta demo (no cambia la de una cuenta existente)' -AsSecureString
        $puntero = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
        try { $secreto = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($puntero) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($puntero) }
    }
    $inicio = New-Object System.Diagnostics.ProcessStartInfo
    $inicio.FileName = Join-Path $JavaHome 'bin\java.exe'
    $inicio.WorkingDirectory = Join-Path $raiz 'Citas'
    $inicio.UseShellExecute = $false
    $inicio.CreateNoWindow = $true
    $inicio.EnvironmentVariables['CITAS_DATOS'] = $DirectorioDatos.Replace('\','/')
    # El lanzador elige esta base; un override heredado no debe abrir otra en silencio.
    $inicio.EnvironmentVariables.Remove('SPRING_DATASOURCE_URL')
    if (-not $SinAdministrador) {
        $inicio.EnvironmentVariables['DEMO_ADMIN_PASSWORD'] = $secreto
        foreach ($par in @(@('DEMO_ADMIN_NOMBRE','Administrador ficticio'),@('DEMO_ADMIN_CEDULA','DEMO-ADMIN'),@('DEMO_ADMIN_CORREO','admin-demo@example.test'))) {
            $valor = [Environment]::GetEnvironmentVariable($par[0])
            if ([string]::IsNullOrWhiteSpace($valor)) { $valor = $par[1] }
            $inicio.EnvironmentVariables[$par[0]] = $valor
        }
    }
    $perfiles = 'persistencia-local'
    if (-not $SinAdministrador) { $perfiles += ',demo-local' }
    if ($RecuperacionLocal) { $perfiles += ',recuperacion-local' }
    $inicio.Arguments = '-jar "' + $jar + '" --spring.profiles.active=' + $perfiles + " --server.address=127.0.0.1 --server.port=$Puerto --recuperacion.smtp.puerto=$Smtp --recuperacion.url-base=http://127.0.0.1:$Puerto --spring.jpa.hibernate.ddl-auto=validate --spring.flyway.enabled=true --spring.flyway.baseline-on-migrate=false --spring.flyway.clean-disabled=true --spring.h2.console.enabled=false --spring.jpa.show-sql=false --logging.level.org.hibernate.SQL=OFF --logging.level.org.springframework.web=INFO --logging.level.org.springframework.security=INFO"
    $proceso = [System.Diagnostics.Process]::Start($inicio)
    $inicio.EnvironmentVariables.Remove('DEMO_ADMIN_PASSWORD')
    $secreto = $null
    @{pid=$proceso.Id;inicioTicks=$proceso.StartTime.ToUniversalTime().Ticks;puerto=$Puerto;jar=$jar;ejecutable=$inicio.FileName;datos=$DirectorioDatos;paradaSolicitada=$false} | ConvertTo-Json | Set-Content -LiteralPath $registro
    Write-Output "Demo en http://127.0.0.1:$Puerto/login. Ctrl+C o scripts/detener-demo-local.ps1 -Puerto $Puerto detiene solo esta JVM."
    while (-not $proceso.WaitForExit(250)) { }
    $parada = $false
    if (Test-Path -LiteralPath $registro) { $parada = (Get-Content -LiteralPath $registro -Raw | ConvertFrom-Json).paradaSolicitada }
    if ($proceso.ExitCode -ne 0 -and -not $parada) { throw 'La demo termino con error. Si la base es incompatible, conservarla y consultar docs/CONFIGURACION_LOCAL.md; no activar baseline ni borrar datos.' }
} finally {
    if ($proceso) {
        if (-not $proceso.HasExited) { $proceso.Kill(); $proceso.WaitForExit() }
        if (Test-Path -LiteralPath $registro) {
            $actual = Get-Content -LiteralPath $registro -Raw | ConvertFrom-Json
            if ($actual.pid -eq $proceso.Id) { Remove-Item -LiteralPath $registro }
        }
        $proceso.Dispose()
    }
    $secreto = $null
    if ($seguro) { $seguro.Dispose() }
}
