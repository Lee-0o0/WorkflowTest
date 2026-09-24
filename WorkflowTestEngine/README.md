# WorkflowTestEngine

纯 Java 执行引擎 JAR，**无 Spring 依赖**。

## 装配方式

```java
WorkflowEngine engine = WorkflowEngines.create();

// 或传入自定义监听器
WorkflowEngine engine = WorkflowEngines.create(List.of(myListener));

// 需要关闭线程池时
try (WorkflowEngines.ManagedWorkflowEngine engine = WorkflowEngines.createManaged(List.of())) {
    engine.execute(request);
}
```

Server 模块通过 Spring `@Bean` 调用 `WorkflowEngines.createManaged(...)` 注入。

## 唯一入口

```java
public interface WorkflowEngine {
    ExecutionHandle execute(ProjectExecutionPlan plan);
    ExecutionHandle execute(GroupExecutionPlan plan);
    ExecutionHandle execute(WorkflowExecutionPlan plan);
    void cancel(String executionId);
}
```

## 依赖

- Jackson（JSON）
- Jayway JsonPath（提取/断言）
- HikariCP + JDBC 驱动（SQL 步骤运行时连接池，由执行计划 `resources.datasources` 注入）
- Lombok（编译期）

SQL 步骤不读数据库配置表；Server 将项目资源（数据源、文件等）解密/解析后写入执行计划的 `resources`，Engine 通过 `RuntimeResourceManager.merge()` 注册。

步骤 `configJson` 在模板替换后会解析为 `executor.config` 包下的强类型配置，执行器不再直接操作 `JsonNode`。

## 目录结构（精简后）

```text
WorkflowTestEngine/
├── WorkflowEngine.java          # 对外唯一入口
├── WorkflowEngines.java         # 无 Spring 装配
├── model/plan/                  # 强类型执行计划
├── listener/                    # 执行监听器 SPI
├── application/execution/       # WorkflowEngineImpl
├── executor/                    # HTTP / SQL / DELAY / SET_VAR / DELETE_VAR 步骤执行器
│   └── config/                  # 各步骤强类型配置（HttpStepConfig 等）
├── model/                       # StepType、EffectiveEnvironment
├── runtime/                     # 运行时上下文与步骤编排
├── spi/                         # ServiceLoader 扩展点
└── support/                     # 常量与 JDBC 配置
```
