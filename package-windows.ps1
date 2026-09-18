param([switch]$Installer)

$ErrorActionPreference = 'Stop'
$workspace = $PSScriptRoot
$dist = Join-Path $workspace 'dist'
$inputDir = Join-Path $workspace 'WorkflowTestDesktop\target\jpackage-input'
$jar = Join-Path $workspace 'WorkflowTestDesktop\target\workflow-test-desktop-1.0.0-SNAPSHOT.jar'

if (-not $env:JAVA_HOME) { throw '请先将 JAVA_HOME 设置为 JDK 21 安装目录。' }
$jpackage = Join-Path $env:JAVA_HOME 'bin\jpackage.exe'
if (-not (Test-Path -LiteralPath $jpackage)) { throw 'JAVA_HOME 中没有 jpackage，请使用完整 JDK 21。' }

& mvn -f (Join-Path $workspace 'pom.xml') clean package
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item -LiteralPath $jar -Destination $inputDir -Force
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$packageType = if ($Installer) { 'exe' } else { 'app-image' }
$existingOutput = if ($Installer) { Join-Path $dist 'WorkflowTest-1.0.0.exe' } else { Join-Path $dist 'WorkflowTest' }
$resolvedDist = [System.IO.Path]::GetFullPath($dist) + [System.IO.Path]::DirectorySeparatorChar
$resolvedOutput = [System.IO.Path]::GetFullPath($existingOutput)
if (-not $resolvedOutput.StartsWith($resolvedDist, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "拒绝清理 dist 目录之外的路径：$resolvedOutput"
}
if (Test-Path -LiteralPath $resolvedOutput) { Remove-Item -LiteralPath $resolvedOutput -Recurse -Force }
$arguments = @(
    '--type', $packageType,
    '--name', 'WorkflowTest',
    '--app-version', '1.0.0',
    '--vendor', 'WorkflowTest',
    '--input', $inputDir,
    '--dest', $dist,
    '--main-jar', (Split-Path $jar -Leaf),
    '--main-class', 'org.springframework.boot.loader.launch.JarLauncher',
    '--java-options', '-Dfile.encoding=UTF-8'
)
if ($Installer) { $arguments += @('--win-menu', '--win-shortcut') }
& $jpackage @arguments
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "打包完成：$dist"
