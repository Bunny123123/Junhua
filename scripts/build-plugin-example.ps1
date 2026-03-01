$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$srcRoot = Join-Path $repoRoot "plugins\sample-plugin\src"
$buildRoot = Join-Path $repoRoot "plugins\sample-plugin\build"
$classesDir = Join-Path $buildRoot "classes"
$bundleDir = Join-Path $repoRoot "plugins\bundle"
$jarPath = Join-Path $bundleDir "mi-plugin.jar"
$jarCmd = "jar"

if ($env:JAVA_HOME) {
  $candidate = Join-Path $env:JAVA_HOME "bin\jar.exe"
  if (Test-Path $candidate) {
    $jarCmd = $candidate
  }
}
if ($jarCmd -eq "jar") {
  $javacCmd = Get-Command javac -ErrorAction SilentlyContinue
  if ($javacCmd -and $javacCmd.Source) {
    $candidate = Join-Path (Split-Path $javacCmd.Source -Parent) "jar.exe"
    if (Test-Path $candidate) {
      $jarCmd = $candidate
    }
  }
}
if ($jarCmd -eq "jar") {
  $javaSettings = cmd /c "java -XshowSettings:properties -version 2>&1"
  $homeLine = $javaSettings | Select-String "java.home"
  if ($homeLine) {
    $javaHome = ($homeLine.ToString() -split "=")[-1].Trim()
    if ($javaHome) {
      $candidate = Join-Path $javaHome "bin\jar.exe"
      if (Test-Path $candidate) {
        $jarCmd = $candidate
      }
    }
  }
}
if ($jarCmd -eq "jar") {
  throw "No se encontro jar.exe (ni por JAVA_HOME ni junto a javac/java)."
}

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
New-Item -ItemType Directory -Force -Path $bundleDir | Out-Null

Write-Host "Compilando plugin externo..." -ForegroundColor Cyan
javac -cp "$repoRoot\lib\xalan-2.7.3.jar;$repoRoot\lib\serializer-2.7.3.jar" `
  -d $classesDir `
  (Get-ChildItem -Path $srcRoot -Recurse -Filter *.java | ForEach-Object { $_.FullName })
if ($LASTEXITCODE -ne 0) {
  throw "Fallo la compilacion del plugin (javac)."
}

Write-Host "Empaquetando JAR..." -ForegroundColor Cyan
& $jarCmd --create --file $jarPath -C $classesDir .
if ($LASTEXITCODE -ne 0) {
  throw "Fallo el empaquetado del JAR. Cierra la app si el archivo esta en uso: $jarPath"
}

Write-Host "OK: $jarPath" -ForegroundColor Green
