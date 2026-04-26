param(
    [Parameter(Mandatory = $true)]
    [string]$StartUrl,
    [int]$MaxPages = 300,
    [string]$StorageDir = 'data\spider',
    [ValidateSet('true', 'false')]
    [string]$SameHostOnly = 'true',
    [int]$Concurrency = 8,
    [int]$SaveEvery = 25,
    [ValidateSet('true', 'false')]
    [string]$WritePageArtifacts = 'false'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot

& (Join-Path $PSScriptRoot 'build.ps1')
Push-Location $projectRoot
try {
    java -cp out hk.ust.csit5930.spider.SpiderApplication $StartUrl $MaxPages $StorageDir $SameHostOnly $Concurrency $SaveEvery $WritePageArtifacts
}
finally {
    Pop-Location
}
