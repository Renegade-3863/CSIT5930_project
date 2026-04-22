param(
    [Parameter(Mandatory = $true)]
    [string]$StartUrl,
    [int]$MaxPages = 300,
    [string]$StorageDir = 'data\spider',
    [ValidateSet('true', 'false')]
    [string]$SameHostOnly = 'true'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'build.ps1')
Push-Location $projectRoot
try {
    java -cp out hk.ust.csit5930.spider.SpiderApplication $StartUrl $MaxPages $StorageDir $SameHostOnly
}
finally {
    Pop-Location
}
