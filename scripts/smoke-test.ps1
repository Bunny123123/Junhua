$ErrorActionPreference = "Stop"

function Step([string]$message) {
  Write-Host "==> $message" -ForegroundColor Cyan
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$pluginTestJava = Join-Path $repoRoot "out\PluginSmokeTest.java"
$pluginTestClass = Join-Path $repoRoot "out\PluginSmokeTest.class"
$pluginJar = Join-Path $repoRoot "plugins\bundle\mi-plugin.jar"

Push-Location $repoRoot
try {
  Step "Build plugin externo"
  try {
    & (Join-Path $repoRoot "scripts\build-plugin-example.ps1")
  } catch {
    if (Test-Path $pluginJar) {
      Write-Host "Aviso: no se pudo regenerar el JAR (posible bloqueo), se reutiliza el existente." -ForegroundColor Yellow
    } else {
      throw
    }
  }

  Step "Compilar aplicacion"
  javac -cp "lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" -d out src\simpleapp\*.java

  Step "Validar carga de plugins al arranque"
  $prevErrorAction = $ErrorActionPreference
  $ErrorActionPreference = "Continue"
  $startupOutput = cmd /c "java -Djava.awt.headless=true -cp out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar simpleapp.Main" 2>&1
  $ErrorActionPreference = $prevErrorAction
  $pluginLines = @($startupOutput | Select-String "\[plugins\]")
  if ($pluginLines.Count -eq 0) {
    throw "No se encontro ninguna linea [plugins] en el arranque."
  }
  $pluginText = ($pluginLines | ForEach-Object { $_.ToString() }) -join "`n"
  foreach ($required in @("saludo ->", "changeImageFormat ->", "miExtension ->")) {
    if (-not $pluginText.Contains($required)) {
      throw "Falta plugin requerido en el arranque: $required"
    }
  }
  $pluginLines | ForEach-Object { Write-Host $_.ToString() -ForegroundColor DarkGray }

  Step "Validar transformacion con plugin externo"
  $testJava = @'
import org.w3c.dom.Document;
import simpleapp.XmlUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PluginSmokeTest {
  public static void main(String[] args) throws Exception {
    Path xml = Paths.get("samples", "collection_example", "collection.xml");
    Path xsl = Paths.get("samples", "collection_plugin_demo.xsl");
    Document source = XmlUtils.parse(xml);
    Document result = XmlUtils.transform(source, xsl);
    XmlUtils.validateCollection(result);
    String text = XmlUtils.toPrettyString(result);
    if (!text.contains("Plugin externo cargado desde JAR")) {
      throw new RuntimeException("No se encontro salida del plugin externo en la transformacion");
    }
    System.out.println("OK plugin transform");
  }
}
'@
  Set-Content -Path $pluginTestJava -Value $testJava
  javac -cp "out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar" -d out $pluginTestJava
  cmd /c "java -cp out;lib\xalan-2.7.3.jar;lib\serializer-2.7.3.jar PluginSmokeTest"

  Write-Host "SMOKE TEST OK" -ForegroundColor Green
}
finally {
  Remove-Item -ErrorAction SilentlyContinue $pluginTestJava, $pluginTestClass
  Pop-Location
}
