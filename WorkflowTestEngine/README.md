# WorkflowTestEngine

独立、无 JavaFX 依赖的工作流测试引擎 JAR。它使用 Spring 非 Web 容器、MyBatis-Plus、Flyway 和 SQLite，对 Desktop 按实体暴露细粒度 Java API，例如 `ProjectService`、`WorkflowDefinitionService`、`StepDefinitionService`、`ProjectExecutionService`、`ExecutionHistoryService` 与 `BackupService`。

在仓库根目录执行 `mvn -pl WorkflowTestEngine test` 可运行引擎集成测试。

步骤类型（HTTP / SQL / DELAY）的配置、提取与断言说明见 [docs/步骤参数说明.md](docs/步骤参数说明.md)。

开发中若修改了 `src/main/resources/db/migration/V1__init.sql`，需删除本地 `%LOCALAPPDATA%\WorkflowTest\data\workflow-test.db` 后重启（应用须已退出，否则文件被锁）。
