# Генератор текстур мода «Зангецу». Рисует пиксель-арт кодом, чтобы его было легко править.
# Запуск (Windows PowerShell 5.1): powershell -ExecutionPolicy Bypass -File tools/textures.ps1 [-Preview]
param([switch]$Preview)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$src = Get-Content -Raw -Encoding UTF8 (Join-Path $PSScriptRoot 'textures.cs')
Add-Type -TypeDefinition $src -ReferencedAssemblies System.Drawing
$root = Split-Path $PSScriptRoot -Parent
$assets = Join-Path $root 'src/main/resources/assets/zangetsu'
$previewDir = ''
if ($Preview) { $previewDir = Join-Path $root 'ref/preview' }
[ZangetsuTextures]::Run($assets, $previewDir)
