$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) {
    throw '请先将 JAVA_HOME 设置为 JDK 21 安装目录。'
}

$javaVersion = & (Join-Path $env:JAVA_HOME 'bin\java.exe') -version 2>&1
if ($javaVersion[0] -notmatch 'version "21') {
    throw "WorkflowTest 需要 JDK 21，当前版本：$($javaVersion[0])"
}

& mvn -pl WorkflowTestDesktop -am install -DskipTests
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& mvn -f (Join-Path $PSScriptRoot 'WorkflowTestDesktop\pom.xml') javafx:run
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
