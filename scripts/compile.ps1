$ErrorActionPreference = "Stop"

# Limpia JAVA_TOOL_OPTIONS solo durante este comando para evitar el error de magick.exe inexistente
$previous = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = ''

Write-Host "Compilando..." -ForegroundColor Cyan
javac -cp "lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" -d out src\simpleapp\*.java

Write-Host "Ejecutando..." -ForegroundColor Cyan
java -cp "out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" simpleapp.Main

# Restaura el valor original si existía
if ($previous) {
  $env:JAVA_TOOL_OPTIONS = $previous
} else {
  Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
