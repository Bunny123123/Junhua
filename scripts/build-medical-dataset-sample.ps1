Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$sampleDir = Join-Path $repoRoot "samples\medical_dataset"
$resourcesDir = Join-Path $sampleDir "resources"
$zipPath = Join-Path $sampleDir "collection.zip"

New-Item -ItemType Directory -Force -Path $resourcesDir | Out-Null

function New-Brush([string]$hex) {
  return New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($hex))
}

function New-Pen([string]$hex, [float]$width) {
  return New-Object System.Drawing.Pen ([System.Drawing.ColorTranslator]::FromHtml($hex)), $width
}

function New-RoundedRectPath([float]$x, [float]$y, [float]$width, [float]$height, [float]$radius) {
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath
  $diameter = $radius * 2
  $path.AddArc($x, $y, $diameter, $diameter, 180, 90)
  $path.AddArc($x + $width - $diameter, $y, $diameter, $diameter, 270, 90)
  $path.AddArc($x + $width - $diameter, $y + $height - $diameter, $diameter, $diameter, 0, 90)
  $path.AddArc($x, $y + $height - $diameter, $diameter, $diameter, 90, 90)
  $path.CloseFigure()
  return $path
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

function Draw-Cover([string]$path, [string]$title, [string]$subtitle) {
  $bmp = New-Object System.Drawing.Bitmap 540, 720
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $rect = [System.Drawing.Rectangle]::new(0, 0, 540, 720)
  $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, ([System.Drawing.ColorTranslator]::FromHtml("#08131B")), ([System.Drawing.ColorTranslator]::FromHtml("#24404E")), 90
  $g.FillRectangle($grad, $rect)
  $grad.Dispose()

  $framePen = New-Pen "#9FD6E9" 4
  $ribPen = New-Pen "#D8EEF4" 5
  $accentBrush = New-Brush "#8ED2E8"
  $softBrush = New-Brush "#15303C"
  $g.DrawRectangle($framePen, 28, 28, 484, 664)
  $g.FillEllipse($softBrush, 120, 168, 120, 250)
  $g.FillEllipse($softBrush, 300, 168, 120, 250)
  for ($y = 192; $y -le 380; $y += 28) {
    $g.DrawArc($ribPen, 98, $y, 160, 72, 204, 130)
    $g.DrawArc($ribPen, 282, $y, 160, 72, -154, 130)
  }
  $g.FillRectangle($accentBrush, 258, 164, 24, 282)
  $g.FillRectangle($accentBrush, 112, 112, 316, 18)

  Draw-CenteredText $g $title 42 "#EAF7FB" ([System.Drawing.RectangleF]::new(52, 474, 436, 92))
  Draw-CenteredText $g $subtitle 23 "#C5E4EE" ([System.Drawing.RectangleF]::new(72, 578, 396, 48)) ([System.Drawing.FontStyle]::Regular)
  Draw-CenteredText $g "Synthetic dataset sample" 20 "#C5E4EE" ([System.Drawing.RectangleF]::new(94, 640, 352, 28)) ([System.Drawing.FontStyle]::Regular)

  $framePen.Dispose()
  $ribPen.Dispose()
  $accentBrush.Dispose()
  $softBrush.Dispose()
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

function Draw-ChestXray([string]$path, [string]$label, [string]$lesion) {
  $bmp = New-Object System.Drawing.Bitmap 520, 640
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml("#0A0E10"))

  $bodyBrush = New-Brush "#11181D"
  $lungBrush = New-Brush "#B9D4DC"
  $bonePen = New-Pen "#E6F2F6" 4
  $spineBrush = New-Brush "#D5EBF2"
  $softPen = New-Pen "#7FA3AF" 2

  $framePath = New-RoundedRectPath 76 34 368 560 34
  $g.FillPath($bodyBrush, $framePath)
  $g.FillEllipse($lungBrush, 118, 146, 114, 270)
  $g.FillEllipse($lungBrush, 288, 146, 114, 270)
  $g.FillRectangle($spineBrush, 244, 118, 30, 340)
  $g.DrawArc($bonePen, 124, 102, 124, 66, 184, 132)
  $g.DrawArc($bonePen, 274, 102, 124, 66, -136, 132)
  for ($y = 168; $y -le 370; $y += 30) {
    $g.DrawArc($softPen, 102, $y, 154, 84, 212, 120)
    $g.DrawArc($softPen, 264, $y, 154, 84, -152, 120)
  }

  if ($lesion -eq "pneumonia") {
    $warmBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(120, 255, 228, 128))
    $hotBrush = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(190, 255, 244, 180))
    $g.FillEllipse($warmBrush, 296, 270, 104, 126)
    $g.FillEllipse($hotBrush, 320, 304, 52, 62)
    $warmBrush.Dispose()
    $hotBrush.Dispose()
  }

  Draw-CenteredText $g $label 22 "#EAF7FB" ([System.Drawing.RectangleF]::new(80, 554, 360, 30)) ([System.Drawing.FontStyle]::Regular)

  $framePath.Dispose()
  $bodyBrush.Dispose()
  $lungBrush.Dispose()
  $bonePen.Dispose()
  $spineBrush.Dispose()
  $softPen.Dispose()
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

function Draw-ForearmXray([string]$path, [string]$label) {
  $bmp = New-Object System.Drawing.Bitmap 520, 640
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml("#090C10"))

  $frameBrush = New-Brush "#111821"
  $boneBrush = New-Brush "#E5EFF3"
  $shadowBrush = New-Brush "#8FA7B2"
  $fracturePen = New-Pen "#FFB86B" 7
  $softPen = New-Pen "#56707D" 3

  $framePath = New-RoundedRectPath 82 36 356 560 34
  $g.FillPath($frameBrush, $framePath)
  $g.FillEllipse($shadowBrush, 162, 112, 74, 360)
  $g.FillEllipse($shadowBrush, 264, 112, 74, 360)
  $g.FillEllipse($boneBrush, 156, 100, 70, 376)
  $g.FillEllipse($boneBrush, 270, 100, 70, 376)
  $g.DrawLine($softPen, 140, 142, 380, 142)
  $g.DrawLine($fracturePen, 230, 408, 280, 370)
  $g.DrawLine($fracturePen, 232, 388, 288, 430)

  Draw-CenteredText $g $label 22 "#EAF7FB" ([System.Drawing.RectangleF]::new(92, 552, 336, 30)) ([System.Drawing.FontStyle]::Regular)

  $framePath.Dispose()
  $frameBrush.Dispose()
  $boneBrush.Dispose()
  $shadowBrush.Dispose()
  $fracturePen.Dispose()
  $softPen.Dispose()
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

function Draw-Heatmap([string]$path, [string]$mode) {
  $bmp = New-Object System.Drawing.Bitmap 520, 640
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml("#071016"))

  $gridPen = New-Pen "#10313A" 1
  for ($x = 40; $x -le 480; $x += 40) { $g.DrawLine($gridPen, $x, 30, $x, 610) }
  for ($y = 30; $y -le 610; $y += 40) { $g.DrawLine($gridPen, 40, $y, 480, $y) }

  if ($mode -eq "normal") {
    $coolBrush = New-Brush "#4FC3F7"
    $g.FillEllipse($coolBrush, 194, 208, 132, 172)
    $coolBrush.Dispose()
  } elseif ($mode -eq "pneumonia") {
    $warmBrush = New-Brush "#F6C65B"
    $hotBrush = New-Brush "#F1664A"
    $g.FillEllipse($warmBrush, 254, 286, 118, 134)
    $g.FillEllipse($hotBrush, 286, 316, 66, 78)
    $warmBrush.Dispose()
    $hotBrush.Dispose()
  } else {
    $warmBrush = New-Brush "#F6C65B"
    $hotBrush = New-Brush "#F1664A"
    $g.FillEllipse($warmBrush, 210, 360, 118, 100)
    $g.FillEllipse($hotBrush, 244, 382, 58, 50)
    $warmBrush.Dispose()
    $hotBrush.Dispose()
  }

  Draw-CenteredText $g "attention map" 20 "#C8E8F1" ([System.Drawing.RectangleF]::new(142, 564, 236, 26)) ([System.Drawing.FontStyle]::Regular)

  $gridPen.Dispose()
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

function Draw-PatientCard([string]$path, [string]$label, [string]$bg, [string]$accent) {
  $bmp = New-Object System.Drawing.Bitmap 420, 420
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml($bg))

  $accentBrush = New-Brush $accent
  $faceBrush = New-Brush "#EAF1F4"
  $softBrush = New-Brush "#183744"
  $g.FillEllipse($faceBrush, 136, 88, 148, 148)
  $g.FillEllipse($softBrush, 150, 60, 120, 90)
  $g.FillRectangle($accentBrush, 118, 232, 184, 104)
  $g.FillEllipse($accentBrush, 92, 252, 58, 80)
  $g.FillEllipse($accentBrush, 270, 252, 58, 80)

  Draw-CenteredText $g $label 26 "#EAF7FB" ([System.Drawing.RectangleF]::new(40, 354, 340, 28))

  $accentBrush.Dispose()
  $faceBrush.Dispose()
  $softBrush.Dispose()
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

function Draw-FindingCard([string]$path, [string]$title, [string]$subtitle, [string]$accent) {
  $bmp = New-Object System.Drawing.Bitmap 420, 320
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
  $g.Clear([System.Drawing.ColorTranslator]::FromHtml("#0C1418"))

  $accentBrush = New-Brush $accent
  $softBrush = New-Brush "#16262E"
  $linePen = New-Pen "#D8EEF4" 4
  $cardPath = New-RoundedRectPath 24 24 372 272 26
  $g.FillPath($softBrush, $cardPath)
  $g.FillEllipse($accentBrush, 46, 64, 86, 86)
  $g.DrawLine($linePen, 86, 96, 330, 96)
  $g.DrawLine($linePen, 86, 150, 330, 150)
  $g.DrawLine($linePen, 86, 204, 286, 204)

  Draw-CenteredText $g $title 28 "#F1FAFD" ([System.Drawing.RectangleF]::new(134, 64, 212, 52))
  Draw-CenteredText $g $subtitle 18 "#CAE3EA" ([System.Drawing.RectangleF]::new(134, 122, 214, 32)) ([System.Drawing.FontStyle]::Regular)

  $cardPath.Dispose()
  $accentBrush.Dispose()
  $softBrush.Dispose()
  $linePen.Dispose()
  $g.Dispose()
  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
  $bmp.Dispose()
}

Set-Content -Path (Join-Path $resourcesDir "dataset-notes.txt") -Encoding UTF8 -Value @"
Dataset: MedVision Synthetic Radiography
Purpose: academic case study for XML collections and record viewer demos
Privacy: all patients and findings are synthetic
Studies:
- CXR-001 normal chest screening
- CXR-002 right lower lobe opacity
- XR-003 distal forearm trauma
"@

Set-Content -Path (Join-Path $resourcesDir "report-cxr-001.txt") -Encoding UTF8 -Value @"
Study: CXR-001
Summary: No acute cardiopulmonary abnormality.
Key points:
- clear lungs
- no pleural effusion
- normal cardiac silhouette
"@

Set-Content -Path (Join-Path $resourcesDir "report-cxr-002.txt") -Encoding UTF8 -Value @"
Study: CXR-002
Summary: Focal right basilar opacity.
Key points:
- probable lower lobe consolidation
- no visible pneumothorax
- correlate with symptoms and labs
"@

Set-Content -Path (Join-Path $resourcesDir "report-xr-003.txt") -Encoding UTF8 -Value @"
Study: XR-003
Summary: Distal radius fracture with mild angulation.
Key points:
- focal cortical break
- ulna preserved
- trauma workflow example
"@

Draw-Cover (Join-Path $resourcesDir "dataset-cover.png") "MEDVISION" "Synthetic radiography collection"

Draw-ChestXray (Join-Path $resourcesDir "study-cxr-001.png") "CXR-001" "normal"
Draw-ChestXray (Join-Path $resourcesDir "study-cxr-002.png") "CXR-002" "pneumonia"
Draw-ForearmXray (Join-Path $resourcesDir "study-xr-003.png") "XR-003"

Draw-Heatmap (Join-Path $resourcesDir "heatmap-cxr-001.png") "normal"
Draw-Heatmap (Join-Path $resourcesDir "heatmap-cxr-002.png") "pneumonia"
Draw-Heatmap (Join-Path $resourcesDir "heatmap-xr-003.png") "fracture"

Draw-PatientCard (Join-Path $resourcesDir "patient-sim-001.png") "SYN-001" "#183340" "#5DB6D1"
Draw-PatientCard (Join-Path $resourcesDir "patient-sim-002.png") "SYN-002" "#2C2F3C" "#8FC16B"
Draw-PatientCard (Join-Path $resourcesDir "patient-sim-003.png") "SYN-003" "#31282A" "#D88C5A"

Draw-FindingCard (Join-Path $resourcesDir "finding-normal.png") "NORMAL" "baseline study" "#4FC3F7"
Draw-FindingCard (Join-Path $resourcesDir "finding-pneumonia.png") "OPACITY" "possible pneumonia" "#F0B84D"
Draw-FindingCard (Join-Path $resourcesDir "finding-fracture.png") "FRACTURE" "distal radius" "#F2735A"

if (Test-Path $zipPath) {
  Remove-Item -Force $zipPath
}
Compress-Archive -Path (Join-Path $sampleDir '*') -DestinationPath $zipPath -Force

Write-Host "Medical sample rebuilt: $zipPath" -ForegroundColor Green
