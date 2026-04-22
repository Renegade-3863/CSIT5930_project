$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$srcDir = Join-Path $projectRoot 'src'
$outDir = Join-Path $projectRoot 'out'

if (Test-Path $outDir) {
    Remove-Item $outDir -Recurse -Force
}
New-Item -ItemType Directory -Path $outDir | Out-Null

$javaFiles = Get-ChildItem -Path $srcDir -Filter *.java -Recurse | Select-Object -ExpandProperty FullName
if (-not $javaFiles) {
    throw 'No Java source files found.'
}

javac -encoding UTF-8 -d $outDir $javaFiles
Write-Output "Compiled $($javaFiles.Count) Java source files into $outDir"
