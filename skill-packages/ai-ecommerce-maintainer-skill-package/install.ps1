param(
    [string]$DestinationRoot = "$env:USERPROFILE\.codex\skills"
)

$ErrorActionPreference = "Stop"

$packageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $packageRoot "skill"
$destination = Join-Path $DestinationRoot "ai-ecommerce-maintainer"

if (-not (Test-Path $source)) {
    throw "Skill source folder not found: $source"
}

New-Item -ItemType Directory -Force -Path $DestinationRoot | Out-Null

if (Test-Path $destination) {
    Remove-Item -LiteralPath $destination -Recurse -Force
}

Copy-Item -Path $source -Destination $destination -Recurse -Force

Write-Host "Installed ai-ecommerce-maintainer skill to:"
Write-Host $destination
Write-Host ""
Write-Host "Usage:"
Write-Host "使用 ai-ecommerce-maintainer skill，接手并分析这个 AI 电商项目。"
