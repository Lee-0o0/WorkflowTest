$ErrorActionPreference = 'Stop'

if (-not $env:JAVA_HOME) { throw '请先将 JAVA_HOME 设置为 JDK 21 安装目录。' }
if (-not $env:WORKFLOWTEST_JWT_SECRET) {
    Write-Warning '当前使用开发 JWT 密钥；生产环境必须设置 WORKFLOWTEST_JWT_SECRET。'
}
& mvn -pl WorkflowTestServer spring-boot:run
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
