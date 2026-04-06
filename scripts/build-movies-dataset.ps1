param(
  [int]$Top = 40,
  [int]$MinRatings = 200
)

$ErrorActionPreference = "Stop"

function New-Slug([string]$text) {
  if ([string]::IsNullOrWhiteSpace($text)) { return "na" }
  $slug = $text.ToLowerInvariant()
  $slug = [Regex]::Replace($slug, "[^a-z0-9]+", "-")
  $slug = $slug.Trim("-")
  if ([string]::IsNullOrWhiteSpace($slug)) { return "na" }
  return $slug
}

function Parse-MovieTitle([string]$rawTitle) {
  if ([string]::IsNullOrWhiteSpace($rawTitle)) {
    return [PSCustomObject]@{ Title = ""; Year = "" }
  }
  $trimmed = $rawTitle.Trim()
  if ($trimmed -match "^(.*)\((\d{4})\)\s*$") {
    return [PSCustomObject]@{
      Title = $matches[1].Trim()
      Year = $matches[2]
    }
  }
  return [PSCustomObject]@{
    Title = $trimmed
    Year = ""
  }
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$targetDir = Join-Path $repoRoot "samples\movies_real"
$collectionPath = Join-Path $targetDir "collection.xml"
$zipOutputPath = Join-Path $targetDir "collection.zip"
$sourceInfoPath = Join-Path $targetDir "source.txt"

$datasetUrl = "https://files.grouplens.org/datasets/movielens/ml-latest-small.zip"
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("movielens_" + [Guid]::NewGuid().ToString("N"))
$zipPath = Join-Path $tempRoot "ml-latest-small.zip"

New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null

try {
  Write-Host "Descargando MovieLens..." -ForegroundColor Cyan
  Invoke-WebRequest -Uri $datasetUrl -OutFile $zipPath

  Write-Host "Extrayendo CSVs..." -ForegroundColor Cyan
  Expand-Archive -Path $zipPath -DestinationPath $tempRoot -Force
  $csvRoot = Join-Path $tempRoot "ml-latest-small"

  $movies = Import-Csv (Join-Path $csvRoot "movies.csv")
  $ratings = Import-Csv (Join-Path $csvRoot "ratings.csv")
  $links = Import-Csv (Join-Path $csvRoot "links.csv")

  $ratingStats = @{}
  foreach ($row in $ratings) {
    $movieId = $row.movieId
    if (-not $ratingStats.ContainsKey($movieId)) {
      $ratingStats[$movieId] = [PSCustomObject]@{ Sum = 0.0; Count = 0 }
    }
    $current = $ratingStats[$movieId]
    $current.Sum += [double]$row.rating
    $current.Count += 1
  }

  $linksByMovie = @{}
  foreach ($row in $links) {
    $linksByMovie[$row.movieId] = $row
  }

  $candidates = @()
  foreach ($movie in $movies) {
    $stats = $ratingStats[$movie.movieId]
    if ($null -eq $stats -or $stats.Count -lt $MinRatings) {
      continue
    }
    $parsed = Parse-MovieTitle $movie.title
    $linkRow = $linksByMovie[$movie.movieId]
    $imdbId = ""
    if ($null -ne $linkRow -and -not [string]::IsNullOrWhiteSpace($linkRow.imdbId)) {
      $imdbId = ("tt" + $linkRow.imdbId.PadLeft(7, "0"))
    }
    $candidates += [PSCustomObject]@{
      MovieId = $movie.movieId
      Title = $parsed.Title
      Year = $parsed.Year
      Genres = $movie.genres
      RatingCount = [int]$stats.Count
      AvgRating = [Math]::Round(($stats.Sum / $stats.Count), 3)
      ImdbId = $imdbId
    }
  }

  $selected = $candidates |
    Sort-Object -Property @{ Expression = "AvgRating"; Descending = $true }, @{ Expression = "RatingCount"; Descending = $true }, @{ Expression = "Title"; Descending = $false } |
    Select-Object -First $Top

  if ($selected.Count -eq 0) {
    throw "No se han seleccionado peliculas. Prueba con un MinRatings menor."
  }

  $genres = New-Object "System.Collections.Generic.HashSet[string]"
  foreach ($movie in $selected) {
    if ([string]::IsNullOrWhiteSpace($movie.Genres)) { continue }
    foreach ($genre in ($movie.Genres -split "\|")) {
      $clean = $genre.Trim()
      if (-not [string]::IsNullOrWhiteSpace($clean) -and $clean -ne "(no genres listed)") {
        [void]$genres.Add($clean)
      }
    }
  }
  $sortedGenres = @($genres | Sort-Object)

  Write-Host "Generando XML de coleccion..." -ForegroundColor Cyan
  $settings = New-Object System.Xml.XmlWriterSettings
  $settings.Indent = $true
  $settings.Encoding = New-Object System.Text.UTF8Encoding($false)

  $writer = [System.Xml.XmlWriter]::Create($collectionPath, $settings)
  try {
    $writer.WriteStartDocument()
    $writer.WriteStartElement("dc")

    foreach ($movie in $selected) {
      $movieObjectId = "movie-$($movie.MovieId)"
      $writer.WriteStartElement("o")
      $writer.WriteAttributeString("id", $movieObjectId)

      $writer.WriteStartElement("pelicula")
      $writer.WriteElementString("titulo", $movie.Title)
      if (-not [string]::IsNullOrWhiteSpace($movie.Year)) {
        $writer.WriteElementString("anio", $movie.Year)
      }
      $writer.WriteElementString("ratingPromedio", ("{0:N3}" -f [double]$movie.AvgRating).Replace(",", "."))
      $writer.WriteElementString("numeroValoraciones", [string]$movie.RatingCount)
      $writer.WriteElementString("generos", $movie.Genres)
      $writer.WriteElementString("fuente", "MovieLens latest-small")
      $writer.WriteEndElement() # pelicula

      $writer.WriteStartElement("rs")
      $writer.WriteStartElement("url")
      $writer.WriteAttributeString("name", "movielens")
      $writer.WriteString("https://movielens.org/movies/$($movie.MovieId)")
      $writer.WriteEndElement()
      if (-not [string]::IsNullOrWhiteSpace($movie.ImdbId)) {
        $writer.WriteStartElement("url")
        $writer.WriteAttributeString("name", "imdb")
        $writer.WriteString("https://www.imdb.com/title/$($movie.ImdbId)/")
        $writer.WriteEndElement()
      }
      $writer.WriteEndElement() # rs

      $writer.WriteStartElement("rels")
      if (-not [string]::IsNullOrWhiteSpace($movie.Genres)) {
        foreach ($genre in ($movie.Genres -split "\|")) {
          $clean = $genre.Trim()
          if ([string]::IsNullOrWhiteSpace($clean) -or $clean -eq "(no genres listed)") {
            continue
          }
          $writer.WriteStartElement("rel")
          $writer.WriteAttributeString("ref", ("genre-" + (New-Slug $clean)))
          $writer.WriteAttributeString("name", "genero")
          $writer.WriteEndElement()
        }
      }
      $writer.WriteEndElement() # rels
      $writer.WriteEndElement() # o
    }

    foreach ($genre in $sortedGenres) {
      $writer.WriteStartElement("o")
      $writer.WriteAttributeString("id", ("genre-" + (New-Slug $genre)))

      $writer.WriteStartElement("genero")
      $writer.WriteElementString("nombre", $genre)
      $writer.WriteElementString("fuente", "MovieLens latest-small")
      $writer.WriteEndElement() # genero

      $writer.WriteEndElement() # o
    }

    $writer.WriteEndElement() # dc
    $writer.WriteEndDocument()
  }
  finally {
    $writer.Dispose()
  }

  if (Test-Path $zipOutputPath) {
    Remove-Item -Force $zipOutputPath
  }
  Compress-Archive -Path $collectionPath -DestinationPath $zipOutputPath -Force

  $sourceInfo = @"
Dataset source: $datasetUrl
Provider: GroupLens Research
Local build date (UTC): $((Get-Date).ToUniversalTime().ToString("o"))
Selection criteria:
  - Top movies: $Top
  - Minimum ratings: $MinRatings
"@
  Set-Content -Path $sourceInfoPath -Value $sourceInfo -Encoding UTF8

  Write-Host "OK: $collectionPath" -ForegroundColor Green
  Write-Host "OK: $zipOutputPath" -ForegroundColor Green
}
finally {
  if (Test-Path $tempRoot) {
    Remove-Item -Recurse -Force $tempRoot
  }
}
