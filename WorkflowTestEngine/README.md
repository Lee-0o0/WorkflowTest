# WorkflowTestEngine

独立、无 JavaFX 依赖的工作流测试引擎 JAR。它使用 Spring 非 Web 容器、MyBatis-Plus、Flyway 和 SQLite，对 Desktop 暴露 `DefinitionService`、`WorkflowExecutionService`、`ExecutionQueryService` 与 `BackupService` Java API。

在仓库根目录执行 `mvn -pl WorkflowTestEngine test` 可运行引擎集成测试。
