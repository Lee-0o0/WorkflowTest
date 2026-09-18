# WorkflowTestDesktop

JavaFX 桌面客户端，采用双模式设计，并且不使用 Spring MVC：

- 本地模式（默认）：通过 WorkflowTestEngine 的 API 使用本机 SQLite，创建、编辑和运行本地测试用例；无需连接 WorkflowTestServer。
- 集中模式（可选）：在独立的“集中资产”窗口登录 WorkflowTestServer，维护公司测试资产，下载已发布执行包后交给本机 Engine 执行。

Desktop 不直接访问 Engine 的 Mapper。Server 未启动、登录失败或网络中断只会影响集中资产窗口，本地主窗口和本地用例执行保持可用。

请从仓库根目录执行 `run.ps1` 启动，或执行 `package-windows.ps1` 生成自带 Java 运行时的 Windows 应用。
