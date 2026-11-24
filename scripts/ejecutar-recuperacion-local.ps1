param(
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidateRange(1024,65535)][int]$Puerto = 18082,
    [ValidateRange(1024,65535)][int]$Smtp = 1025
)
$ErrorActionPreference = 'Stop'
if (-not $JavaHome) { throw 'Indicar -JavaHome con JDK 26.' }
$proyecto = (Resolve-Path (Join-Path $PSScriptRoot '..\Citas')).Path
$jar = Join-Path $proyecto 'target\Citas-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Ejecutar scripts/verificar.ps1 antes de iniciar.' }
Push-Location -LiteralPath $proyecto
try {
    # Una H2 exclusiva en memoria. No usa ni reinicia bases persistentes existentes.
    & (Join-Path $JavaHome 'bin\java.exe') -jar $jar '--spring.profiles.active=recuperacion-local' "--server.port=$Puerto" '--server.address=127.0.0.1' "--recuperacion.smtp.puerto=$Smtp" "--recuperacion.url-base=http://127.0.0.1:$Puerto" '--spring.datasource.url=jdbc:h2:mem:demo02A;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' '--spring.jpa.hibernate.ddl-auto=create-drop' '--spring.h2.console.enabled=false' '--spring.jpa.show-sql=false' '--logging.level.org.hibernate.SQL=OFF' '--logging.level.org.springframework.web=INFO' '--logging.level.org.springframework.security=INFO'
    if ($LASTEXITCODE -ne 0) { throw 'La instancia local termino con error.' }
} finally { Pop-Location }
