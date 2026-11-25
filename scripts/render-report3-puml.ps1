[CmdletBinding()]
param(
    [Parameter()]
    [string] $Source = "",

    [Parameter()]
    [string] $Output = "",

    [Parameter()]
    [string] $PlantUmlJar = "",

    [Parameter()]
    [switch] $CheckOnly,

    [Parameter()]
    [switch] $CleanOutput
)

$ErrorActionPreference = "Stop"

function Resolve-ProjectPath {
    param([string] $PathValue)

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }

    if ([System.IO.Path]::IsPathRooted($PathValue)) {
        return [System.IO.Path]::GetFullPath($PathValue)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\$PathValue"))
}

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$defaultSource = Join-Path $projectRoot "documents\docs\report-3-software-requirement-specification\assets\diagrams"
$defaultOutput = Join-Path $projectRoot "report3-rendered-png"
$defaultJar = Join-Path $projectRoot "plantuml.jar"

$sourcePath = if ($Source) { Resolve-ProjectPath $Source } else { $defaultSource }
$outputPath = if ($Output) { Resolve-ProjectPath $Output } else { $defaultOutput }
$jarPath = if ($PlantUmlJar) { Resolve-ProjectPath $PlantUmlJar } else { $defaultJar }

if (-not (Test-Path -LiteralPath $sourcePath -PathType Container)) {
    throw "Report 3 PlantUML source folder was not found: $sourcePath"
}

if (-not (Test-Path -LiteralPath $jarPath -PathType Leaf)) {
    throw "PlantUML JAR was not found: $jarPath`nPlace plantuml.jar at the repository root or pass -PlantUmlJar <path>."
}

$pumlFiles = @(Get-ChildItem -LiteralPath $sourcePath -Filter "*.puml" -File -Recurse | Sort-Object FullName)
if ($pumlFiles.Count -eq 0) {
    throw "No .puml files were found under: $sourcePath"
}

Write-Host "Report 3 PlantUML files: $($pumlFiles.Count)"
Write-Host "Source: $sourcePath"
Write-Host "PlantUML: $jarPath"

$java = Get-Command java -ErrorAction SilentlyContinue
if (-not $java) {
    throw "Java was not found on PATH. Install a JRE/JDK and run the script again."
}

if ($CleanOutput -and (Test-Path -LiteralPath $outputPath)) {
    Remove-Item -LiteralPath $outputPath -Recurse -Force
}

if (-not $CheckOnly) {
    New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
}

if ($CheckOnly) {
    foreach ($file in $pumlFiles) {
        Write-Host "Checking $($file.FullName)"
        & $java.Source "-jar" $jarPath "-charset" "UTF-8" "-checkonly" $file.FullName
        if ($LASTEXITCODE -ne 0) {
            throw "PlantUML syntax check failed for: $($file.FullName)"
        }
    }
    Write-Host "PlantUML syntax check passed for all Report 3 diagrams."
} else {
    Write-Host "Rendering all Report 3 diagrams in one PlantUML invocation."
    $fileArguments = @($pumlFiles | ForEach-Object { $_.FullName })
    & $java.Source "-jar" $jarPath "-charset" "UTF-8" "-tpng" "-output" $outputPath @fileArguments
    if ($LASTEXITCODE -ne 0) {
        throw "PlantUML rendering failed."
    }
    $pngFiles = @(Get-ChildItem -LiteralPath $outputPath -Filter "*.png" -File -Recurse)
    Write-Host "Rendered PNG files: $($pngFiles.Count)"
    Write-Host "Output: $outputPath"
}
