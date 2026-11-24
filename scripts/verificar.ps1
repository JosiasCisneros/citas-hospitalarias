param([string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if ($JavaHome) { $env:JAVA_HOME = $JavaHome }
$proyecto = Join-Path $PSScriptRoot '..\Citas'
Push-Location -LiteralPath $proyecto
try {
    & .\mvnw.cmd -v
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo iniciar el wrapper Maven.' }
    & .\mvnw.cmd -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw 'Fallaron las comprobaciones Maven. Revisar target/surefire-reports.' }
} finally {
    Pop-Location
}
