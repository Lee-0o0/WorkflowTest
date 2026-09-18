# WorkflowTest 一期

WorkflowTest 是一个桌面接口自动化工具。Desktop 和 Engine 不启动 Spring MVC 或任何后台 Web 服务；可选的 Server 独立部署，用于集中管理公司测试资产。

- `WorkflowTestEngine`：测试定义、四级环境变量、钩子、HTTP/SQL/Delay 执行、记录与 SQLite 持久化。
- `WorkflowTestDesktop`：JavaFX 桌面界面，通过 Engine Java API 在同一 JVM 中运行。
- `WorkflowTestServer`：公司测试资产、用户、项目权限、工作流版本和执行包的集中管理服务。

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

Desktop 默认进入“本地工作台”，不要求 WorkflowTestServer 已启动，也不会在启动时连接 Server。本地项目、组、工作流和执行步骤保存在本机 SQLite 中；Server 未部署、未登录或暂时断网时，仍可完成下面全部本地操作。

1. 新建项目、组和工作流。
2. 选择项目、组或工作流，配置各层环境变量；`${env.xxx}` 读取合并后的环境变量。
3. 选择项目管理运行时数据源。
4. 选择组或工作流添加前置钩子步骤。
5. 给工作流添加 HTTP、SQL 或 Delay 步骤；`${vars.xxx}` 读取运行时变量，`${steps.步骤编码.xxx}` 读取步骤输出。
6. 选择工作流单独运行，或选择组串行运行全部工作流。
7. 在“执行日志”和“历史记录”中查看结果；双击工作流历史可查看步骤详情。

需要使用公司集中资产时，点击“集中资产（可选）”，输入 Server 地址并登录。集中工作流的执行包由 Server 下载，但 HTTP、SQL 和 Delay 步骤仍在当前用户电脑中由 Engine 执行。连接或登录失败只影响集中资产窗口，不影响本地工作台及本地 SQLite 用例。

步骤、工作流和组可用工具栏“上移/下移”调整顺序。敏感变量和数据源密码加密保存，界面与执行快照不显示明文。

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
