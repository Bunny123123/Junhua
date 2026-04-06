Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sampleDir = Join-Path $repoRoot "samples\movie_collection"
$resourcesDir = Join-Path $sampleDir "resources"
$zipPath = Join-Path $sampleDir "collection.zip"

New-Item -ItemType Directory -Force -Path $resourcesDir | Out-Null

function New-Brush([string]$hex) {
  return New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($hex))
}

function Draw-CenteredText($graphics, [string]$text, [float]$size, [string]$hex, [System.Drawing.RectangleF]$rect, [System.Drawing.FontStyle]$style = [System.Drawing.FontStyle]::Bold) {
  $font = New-Object System.Drawing.Font("Arial", $size, $style, [System.Drawing.GraphicsUnit]::Pixel)
  $brush = New-Brush $hex
  $format = New-Object System.Drawing.StringFormat
  $format.Alignment = [System.Drawing.StringAlignment]::Center
  $format.LineAlignment = [System.Drawing.StringAlignment]::Center
  $graphics.DrawString($text, $font, $brush, $rect, $format)
  $font.Dispose()
  $brush.Dispose()
  $format.Dispose()
}

function Draw-Poster([string]$path, [string]$title, [string]$subtitle, [string]$bg1, [string]$bg2, [string]$accent) {
  $bmp = New-Object System.Drawing.Bitmap 500, 720
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $rect = [System.Drawing.Rectangle]::new(0, 0, 500, 720)
  $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.ColorTranslator]::FromHtml($bg1)), ([System.Drawing.ColorTranslator]::FromHtml($bg2)), 90
  $g.FillRectangle($grad, $rect)
  $grad.Dispose()

  $accentBrush = New-Brush $accent
  $lightBrush = New-Brush "#FFF8EE"
  $g.FillRectangle($accentBrush, 30, 28, 440, 18)
  $g.FillEllipse($accentBrush, 150, 392, 200, 200)
  $g.FillRectangle($lightBrush, 118, 492, 264, 20)
  $g.FillRectangle($lightBrush, 142, 540, 216, 20)

  Draw-CenteredText $g $title 54 "#FFF8EE" ([System.Drawing.RectangleF]::new(48, 110, 404, 160))
  Draw-CenteredText $g $subtitle 21 "#FFF8EE" ([System.Drawing.RectangleF]::new(40, 250, 420, 48)) ([System.Drawing.FontStyle]::Regular)
  Draw-CenteredText $g "Movie collection sample" 22 "#FFF8EE" ([System.Drawing.RectangleF]::new(60, 640, 380, 34)) ([System.Drawing.FontStyle]::Regular)

  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $accentBrush.Dispose()
  $lightBrush.Dispose()
  $g.Dispose()
  $bmp.Dispose()
}

function Draw-Portrait([string]$path, [string]$label, [string]$bg, [string]$skin, [string]$hair, [string]$jacket) {
  $bmp = New-Object System.Drawing.Bitmap 420, 420
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml($bg))

  $skinBrush = New-Brush $skin
  $hairBrush = New-Brush $hair
  $jacketBrush = New-Brush $jacket
  $eyeBrush = New-Brush "#FFF8EE"

  $g.FillEllipse($hairBrush, 108, 46, 204, 128)
  $g.FillEllipse($skinBrush, 122, 76, 176, 176)
  $g.FillRectangle($jacketBrush, 110, 246, 200, 118)
  $g.FillEllipse($jacketBrush, 90, 268, 56, 88)
  $g.FillEllipse($jacketBrush, 274, 268, 56, 88)
  $g.FillEllipse($eyeBrush, 172, 144, 14, 8)
  $g.FillEllipse($eyeBrush, 232, 144, 14, 8)

  Draw-CenteredText $g $label 26 "#FFF8EE" ([System.Drawing.RectangleF]::new(30, 368, 360, 28))

  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $skinBrush.Dispose()
  $hairBrush.Dispose()
  $jacketBrush.Dispose()
  $eyeBrush.Dispose()
  $g.Dispose()
  $bmp.Dispose()
}

function Draw-Character([string]$path, [string]$label, [string]$bg, [string]$face, [string]$hair, [string]$body, [string]$visor) {
  $bmp = New-Object System.Drawing.Bitmap 360, 360
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml($bg))

  $faceBrush = New-Brush $face
  $hairBrush = New-Brush $hair
  $bodyBrush = New-Brush $body
  $visorBrush = New-Brush $visor
  $eyeBrush = New-Brush "#F5F7FA"

  $g.FillEllipse($hairBrush, 102, 40, 156, 118)
  $g.FillEllipse($faceBrush, 110, 68, 140, 140)
  $g.FillRectangle($bodyBrush, 94, 204, 172, 114)
  $g.FillRectangle($visorBrush, 126, 122, 108, 16)
  $g.FillEllipse($eyeBrush, 154, 132, 12, 8)
  $g.FillEllipse($eyeBrush, 194, 132, 12, 8)

  Draw-CenteredText $g $label 24 "#F5F7FA" ([System.Drawing.RectangleF]::new(18, 316, 324, 28))

  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $faceBrush.Dispose()
  $hairBrush.Dispose()
  $bodyBrush.Dispose()
  $visorBrush.Dispose()
  $eyeBrush.Dispose()
  $g.Dispose()
  $bmp.Dispose()
}

Draw-Poster (Join-Path $resourcesDir "poster-matrix.png") "THE MATRIX" "Wake up and follow the signal" "#061511" "#184F43" "#7DFFB5"
Draw-Poster (Join-Path $resourcesDir "poster-starwars.png") "STAR WARS" "A New Hope" "#0B1025" "#45214A" "#FFD258"
Draw-Poster (Join-Path $resourcesDir "poster-pulpfiction.png") "PULP FICTION" "Crime and conversation" "#6E1616" "#D3982E" "#1C1412"

Draw-Portrait (Join-Path $resourcesDir "author-wachowskis.png") "WACHOWSKIS" "#18273D" "#7BEAD1" "#0B1320" "#2A6078"
Draw-Portrait (Join-Path $resourcesDir "author-george-lucas.png") "GEORGE LUCAS" "#241833" "#F1C98F" "#2E2940" "#324770"
Draw-Portrait (Join-Path $resourcesDir "author-tarantino.png") "TARANTINO" "#391918" "#F1C692" "#131313" "#1D1514"

Draw-Character (Join-Path $resourcesDir "char-neo.png") "NEO" "#0D211A" "#A8FACC" "#111" "#162B24" "#82FFC0"
Draw-Character (Join-Path $resourcesDir "char-trinity.png") "TRINITY" "#172A3E" "#B9F4FF" "#0B1320" "#1B3245" "#82E5FF"
Draw-Character (Join-Path $resourcesDir "char-luke.png") "LUKE" "#261C37" "#FFE2A0" "#E3B76A" "#F6F0DA" "#7DEBFF"
Draw-Character (Join-Path $resourcesDir "char-vader.png") "VADER" "#0C1221" "#D3DCE8" "#10151F" "#252C40" "#A8B7D2"
Draw-Character (Join-Path $resourcesDir "char-vincent.png") "VINCENT" "#391817" "#F2C895" "#171110" "#1B1515" "#ECCAA3"
Draw-Character (Join-Path $resourcesDir "char-jules.png") "JULES" "#29140F" "#D6A06D" "#0E0E0E" "#17110F" "#F0D3B7"

if (Test-Path $zipPath) {
  Remove-Item -Force $zipPath
}
Compress-Archive -Path (Join-Path $sampleDir '*') -DestinationPath $zipPath -Force

Write-Host "Movie sample rebuilt: $zipPath" -ForegroundColor Green
