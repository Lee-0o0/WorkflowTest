# WorkflowTest

本地工作流自动化测试工具：**Vue 前端 + HTTP Server（CRUD）+ Engine（纯执行）**。

## 架构

```text
WorkflowTestWeb (Vue 3)
        │ HTTP /api/*
        ▼
WorkflowTestServer (Spring Boot, :8080)
  ├── REST CRUD：项目 / 组 / 工作流 / 步骤 / 变量 / 资源
  ├── SQLite + MyBatis-Plus + Flyway
  └── 组装执行计划 ──► WorkflowEngine.execute()
                              │
WorkflowTestEngine (纯执行 JAR，无实体/持久化)
  └── HTTP / SQL / DELAY 步骤运行时
```

| 模块 | 职责 |
|---|---|
| `WorkflowTestServer` | 全部增删改查、实体持久化、执行计划组装、触发执行 |
| `WorkflowTestEngine` | **纯 Java 执行 JAR**（无 Spring），唯一入口 `WorkflowEngine.execute()` |
| `WorkflowTestWeb` | Vue 管理界面 |

## Engine 接口

```java
public interface WorkflowEngine {
    ExecutionHandle execute(ProjectExecutionPlan plan);
    ExecutionHandle execute(GroupExecutionPlan plan);
    ExecutionHandle execute(WorkflowExecutionPlan plan);
    void cancel(String executionId);
}
```

Server 从数据库加载定义，组装强类型执行计划（`ProjectExecutionPlan` / `GroupExecutionPlan` / `WorkflowExecutionPlan`），再调用 Engine 执行。

## 快速开始

```bash
# 启动 Server（需 JDK 21，local 环境自动绑定空闲端口）
mvn -pl WorkflowTestServer spring-boot:run

# 固定 8080 端口（生产部署）
# set SPRING_PROFILES_ACTIVE=prod && mvn -pl WorkflowTestServer spring-boot:run

# 启动 Vue 前端
cd WorkflowTestWeb && npm install && npm run dev
```

访问 `http://localhost:5173`。
