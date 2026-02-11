
# Empaqueta la colección de ejemplo en un ZIP listo para la app
param(
    [string]$Source = "samples/collection_example",
    [string]$Dest = "samples/collection.zip"
)

if (-not (Test-Path $Source)) {
    Write-Error "No existe la carpeta '$Source'"
    exit 1
}

if (Test-Path $Dest) { Remove-Item -Force $Dest }
Compress-Archive -Path (Join-Path $Source '*') -DestinationPath $Dest -Force
Write-Host "ZIP creado: $Dest"