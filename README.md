# WorkflowTest 一期

WorkflowTest 是一个桌面接口自动化工具。Desktop 和 Engine 不启动 Spring MVC 或任何后台 Web 服务，全部能力在本机完成。

- `WorkflowTestEngine`：测试定义、四级环境变量、钩子、HTTP/SQL/Delay 执行、记录与 SQLite 持久化。
- `WorkflowTestDesktop`：JavaFX 桌面界面，通过 Engine Java API 在同一 JVM 中运行。

测试工程按“项目 → 组 → 工作流 → 执行步骤”组织。环境变量优先级为：Spring Environment 全局变量 < 项目变量 < 组变量 < 工作流变量。

## 开发环境

- Windows 10/11
- JDK 21（必须包含 `jpackage`）
- Maven 3.9+
- 生成 EXE 安装包时需要 WiX Toolset 3.x；生成便携目录不需要 WiX

## 构建和启动

在工程根目录执行：

```powershell
mvn clean test
.\run.ps1
```

如果 JDK 21 没有配置为系统默认版本：

```powershell
$env:JAVA_HOME = 'D:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\run.ps1
```

## 使用顺序

本地项目、组、工作流和执行步骤保存在本机 SQLite 中，无需网络连接。

1. 新建项目、组和工作流。
2. 选择项目、组或工作流，配置各层环境变量；按层引用 `${global.xxx}` / `${project.xxx}` / `${group.xxx}` / `${workflow.xxx}`（或省略前缀表示工作流层）。
3. 选择项目管理运行时数据源。
4. 选择组或工作流添加前置钩子步骤。
5. 给工作流添加 HTTP、SQL 或 Delay 步骤；提取变量写入 `${group.xxx}` 或 `${workflow.xxx}`，`${steps.步骤编码.xxx}` 读取步骤输出。
6. 选择工作流单独运行，或选择组串行运行全部工作流。
7. 在“执行日志”和“历史记录”中查看结果；双击工作流历史可查看步骤详情。

步骤、工作流和组可用工具栏“上移/下移”调整顺序。数据源密码加密保存，界面不显示明文。

## 本地数据

默认路径：

```text
%LOCALAPPDATA%\WorkflowTest\data\workflow-test.db
%LOCALAPPDATA%\WorkflowTest\logs\workflow-test.log
```

桌面工具栏“备份”会生成一个事务一致的 SQLite 备份文件。恢复时请先退出程序，再用备份文件替换 `workflow-test.db`；建议替换前另存当前数据库。

## Windows 打包

生成无需预装 Java 的便携应用目录：

```powershell
.\package-windows.ps1
```

输出在 `dist\WorkflowTest`。生成 EXE 安装包：

```powershell
.\package-windows.ps1 -Installer
```

完整架构、数据模型、变量和钩子语义见 [WorkflowTest-技术方案.md](WorkflowTest-技术方案.md)。
