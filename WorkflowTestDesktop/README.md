# WorkflowTestDesktop

JavaFX 桌面客户端，采用双模式设计，并且不使用 Spring MVC：

- 本地模式（默认）：通过 WorkflowTestEngine 的 API 使用本机 SQLite，创建、编辑和运行本地测试用例；无需连接 WorkflowTestServer。
- 集中模式（可选）：在独立的“集中资产”窗口登录 WorkflowTestServer，维护公司测试资产，下载已发布执行包后交给本机 Engine 执行。

Desktop 不直接访问 Engine 的 Mapper。Server 未启动、登录失败或网络中断只会影响集中资产窗口，本地主窗口和本地用例执行保持可用。

请从仓库根目录执行 `run.ps1` 启动，或执行 `package-windows.ps1` 生成自带 Java 运行时的 Windows 应用。

## 在 IDE 中运行

JDK 11 起 JavaFX 不再内置，**不能**直接运行 `WorkflowTestApplication` 而不配置 JavaFX 模块路径。

推荐方式（任选其一）：

1. **命令行**：`.\run.ps1` 或 `mvn -f WorkflowTestDesktop/pom.xml javafx:run`
2. **Cursor / VS Code**：使用调试面板中的 **WorkflowTest Desktop** 启动配置（会自动复制 JavaFX 依赖并附加 `--module-path`）
3. **IntelliJ IDEA**：Run Configuration → VM options：

   ```text
   --module-path WorkflowTestDesktop/target/javafx-lib --add-modules javafx.controls,javafx.fxml
   ```

   首次运行前先执行：`mvn -pl WorkflowTestDesktop -am process-classes -DskipTests`

## 本地数据库与 Flyway

本地 SQLite 位于 `%LOCALAPPDATA%\WorkflowTest\data\workflow-test.db`。

若启动报错 `Migration checksum mismatch for migration version 1`，说明 **`V1__init.sql` 已变更**，但本地库仍保留旧迁移记录。MVP 阶段请：

1. **先完全退出** Desktop（关闭 IntelliJ 运行进程，避免 SQLite 文件被占用）
2. 删除 `%LOCALAPPDATA%\WorkflowTest\data\workflow-test.db`
3. 重新启动应用（Flyway 会按最新 `V1__init.sql` 重建库）

本地测试数据会清空；如需保留，请先通过应用内备份功能导出。
