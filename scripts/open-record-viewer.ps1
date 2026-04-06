$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$previous = $env:JAVA_TOOL_OPTIONS
$env:JAVA_TOOL_OPTIONS = ''

$recordPath = if ($args.Count -gt 0) { $args[0] } else { $null }

Push-Location $repoRoot
try {
  Write-Host "Compilando visor..." -ForegroundColor Cyan
  javac -cp "lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" -d out src\simpleapp\*.java

  Write-Host "Abriendo visor externo..." -ForegroundColor Cyan
  if ($recordPath) {
    java -cp "out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" simpleapp.RecordViewerMain $recordPath
  } else {
    java -cp "out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" simpleapp.RecordViewerMain
  }
}
finally {
  Pop-Location
  if ($previous) {
    $env:JAVA_TOOL_OPTIONS = $previous
  } else {
    Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
  }
}
