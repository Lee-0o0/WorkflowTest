# WorkflowTest 桌面自动化测试工具技术方案

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 产品名称 | WorkflowTest |
| 核心项目 | WorkflowTestEngine |
| 桌面项目 | WorkflowTestDesktop |
| 产品形态 | Windows 桌面应用 |
| 目标用户 | 接口测试人员、开发人员、质量保障人员 |
| 核心能力 | HTTP/SQL 测试步骤、多步骤编排、变量传递、断言、执行报告 |

## 2. 建设目标

WorkflowTest 是一个以数据驱动方式定义测试流程的桌面自动化测试工具。测试工程按照“项目 → 组 → 工作流 → 执行步骤”组织。测试人员通过桌面界面配置工作流，不需要编写 Java 测试类，即可完成分层环境变量管理、执行前钩子、接口调用、数据库查询、跨步骤传值、结果断言和执行报告查看。

系统拆分为两个独立项目：

1. `WorkflowTestEngine`：独立的工作流测试引擎 Java 库，负责领域模型、工作流执行、数据持久化和步骤扩展。
2. `WorkflowTestDesktop`：独立的 JavaFX 桌面应用，负责用例设计、配置管理、运行控制和报告展示。

两个项目在代码仓库、构建产物和职责上独立，但运行时位于同一个 JVM 进程。Desktop 通过 Java 接口调用 Engine，不使用 Spring MVC、REST API 或内置 Web 服务器。

## 3. 总体架构

```text
┌──────────────────────────────────────────────────────┐
│ WorkflowTestDesktop                                  │
│                                                      │
│ JavaFX View                                          │
│      ↓                                               │
│ Controller / ViewModel                               │
│      ↓                                               │
│ Desktop Application Service                          │
│      ↓ Java API                                      │
├──────────────────────────────────────────────────────┤
│ WorkflowTestEngine                                   │
│                                                      │
│ Engine Facade                                        │
│      ↓                                               │
│ Workflow Runtime                                     │
│      ├── Variable Resolver                           │
│      ├── HTTP Step Executor                          │
│      ├── SQL Step Executor                           │
│      ├── Extraction Engine                           │
│      └── Assertion Engine                            │
│      ↓                                               │
│ MyBatis-Plus                                         │
│      ↓                                               │
│ SQLite（默认）/ MySQL / PostgreSQL（预留）            │
└──────────────────────────────────────────────────────┘
```

### 3.1 运行形态

```text
一个桌面进程
  ├── JavaFX Application Thread
  ├── Spring 非 Web ApplicationContext
  ├── Workflow 执行线程池
  ├── WorkflowTestEngine
  └── SQLite
```

Spring 只负责依赖注入、配置、事务、MyBatis-Plus 集成和组件生命周期，不启动 Tomcat，不监听网络端口。

### 3.2 数据库边界

系统涉及两类数据库，必须严格区分：

| 类型 | 作用 | 访问方式 |
|---|---|---|
| 平台元数据库 | 保存工作流、环境、步骤、执行记录和报告 | MyBatis-Plus |
| 被测业务数据库 | SQL Step 查询或准备被测系统数据 | JDBC + HikariCP |

默认平台元数据库为 SQLite。被测业务数据库可以同时配置多个，初期支持 SQLite、MySQL、PostgreSQL，后续可扩展 SQL Server、Oracle。

## 4. 技术选型

| 类别 | 技术 | 说明 |
|---|---|---|
| JDK | Java 21 | 使用 LTS 版本 |
| 应用容器 | Spring Boot 3.x | 以 `WebApplicationType.NONE` 启动 |
| 桌面界面 | JavaFX | 桌面 UI |
| 持久化 | MyBatis-Plus | 平台元数据 CRUD、分页和条件查询 |
| 默认数据库 | SQLite | 零安装、适合单机桌面应用 |
| 数据库迁移 | Flyway | 数据库版本升级 |
| HTTP 客户端 | Java HttpClient 或 RestAssured | 执行 HTTP Step |
| 连接池 | HikariCP | 动态业务数据源连接池 |
| JSON | Jackson | 配置和执行结果序列化 |
| JSON 提取 | Jayway JsonPath | 响应及 SQL 结果提取 |
| 日志 | SLF4J + Logback | 本地滚动日志 |
| 测试 | JUnit 5 + Mockito + Testcontainers | 测试项目自身 |
| 打包 | Maven + jpackage | 生成 Windows 安装程序 |

JUnit 只用于 Engine 和 Desktop 自身的单元测试、集成测试，不作为动态工作流的核心执行器。

---

# 第一部分：WorkflowTestEngine

## 5. 项目定位

`WorkflowTestEngine` 是不依赖 UI 的工作流测试引擎。它以普通 JAR 的形式发布，由 Desktop 直接依赖。

Engine 负责：

- 工作流、环境、数据源和执行记录的增删改查。
- 工作流版本发布与不可变快照。
- HTTP、SQL、Delay 等步骤的解释和执行。
- 多步骤之间的类型化变量传递。
- 响应提取、结果断言、失败策略、重试和超时。
- 执行事件通知、取消执行和报告生成。
- SQLite 元数据持久化及数据库升级。

Engine 不负责：

- JavaFX 控件和页面。
- 桌面窗口生命周期。
- REST API、Spring MVC 或 Web Server。
- 在 Engine 工作线程中直接更新 UI。

## 6. Maven 坐标与依赖方式

建议 Maven 坐标：

```xml
<groupId>com.example.workflowtest</groupId>
<artifactId>workflow-test-engine</artifactId>
<version>1.0.0-SNAPSHOT</version>
<packaging>jar</packaging>
```

Desktop 引用：

```xml
<dependency>
    <groupId>com.example.workflowtest</groupId>
    <artifactId>workflow-test-engine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

开发阶段可通过 Maven 多仓库本地安装、私有 Maven 仓库或同一父工程的多模块聚合构建管理版本。

## 7. 推荐目录结构

```text
WorkflowTestEngine/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/example/workflowtest/engine/
    │   │   ├── api/
    │   │   │   ├── WorkflowService.java
    │   │   │   ├── ProjectService.java
    │   │   │   ├── WorkflowGroupService.java
    │   │   │   ├── ScopedVariableService.java
    │   │   │   ├── RuntimeDataSourceService.java
    │   │   │   ├── WorkflowExecutionService.java
    │   │   │   └── ExecutionQueryService.java
    │   │   ├── application/
    │   │   │   ├── workflow/
    │   │   │   ├── variable/
    │   │   │   ├── datasource/
    │   │   │   └── execution/
    │   │   ├── domain/
    │   │   │   ├── workflow/
    │   │   │   ├── variable/
    │   │   │   ├── datasource/
    │   │   │   └── execution/
    │   │   ├── runtime/
    │   │   │   ├── WorkflowEngine.java
    │   │   │   ├── ExecutionContext.java
    │   │   │   ├── VariableResolver.java
    │   │   │   ├── ExtractionEngine.java
    │   │   │   ├── AssertionEngine.java
    │   │   │   └── RetryExecutor.java
    │   │   ├── executor/
    │   │   │   ├── StepExecutor.java
    │   │   │   ├── StepExecutorRegistry.java
    │   │   │   ├── http/
    │   │   │   ├── sql/
    │   │   │   └── delay/
    │   │   ├── persistence/
    │   │   │   ├── entity/
    │   │   │   ├── mapper/
    │   │   │   ├── repository/
    │   │   │   └── typehandler/
    │   │   ├── event/
    │   │   ├── exception/
    │   │   └── config/
    │   └── resources/
    │       ├── mapper/
    │       └── db/migration/
    └── test/
```

### 7.1 MyBatis-Plus 持久化约定

Engine 使用 MyBatis-Plus 完成平台元数据的常规 CRUD、条件查询和分页。推荐依赖：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>

<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>

<dependency>
    <groupId>org.xerial</groupId>
    <artifactId>sqlite-jdbc</artifactId>
</dependency>
```

具体版本由父 POM 的 `dependencyManagement` 锁定，禁止各模块分别声明不同版本。

配置示例：

```yaml
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*.xml
  type-handlers-package: com.example.workflowtest.engine.persistence.typehandler
  configuration:
    map-underscore-to-camel-case: true
    call-setters-on-nulls: true
  global-config:
    banner: false
    db-config:
      id-type: assign_uuid
```

插件配置：

```java
@Configuration
@MapperScan("com.example.workflowtest.engine.persistence.mapper")
public class MyBatisPlusConfiguration {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(
            StorageDatabaseProperties properties
    ) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(
                new PaginationInnerInterceptor(properties.dbType())
        );
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
```

`StorageDatabaseProperties.dbType()` 根据平台元数据库配置返回 `DbType.SQLITE`、`DbType.MYSQL` 或 `DbType.POSTGRE_SQL`，切换数据库时不修改业务代码。

实体和 Mapper 示例：

```java
@TableName(value = "ts_workflow", autoResultMap = true)
public class WorkflowEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String groupId;
    private String name;
    private String description;
    private Integer currentVersion;
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

```java
public interface WorkflowMapper extends BaseMapper<WorkflowEntity> {
}
```

Repository 负责领域模型与 Entity 转换，应用层不能直接依赖 Mapper：

```java
@Repository
public class MyBatisPlusWorkflowRepository implements WorkflowRepository {

    private final WorkflowMapper workflowMapper;

    @Override
    public List<Workflow> findByGroupId(String groupId) {
        return workflowMapper.selectList(
                        Wrappers.<WorkflowEntity>lambdaQuery()
                                .eq(WorkflowEntity::getGroupId, groupId)
                                .orderByDesc(WorkflowEntity::getUpdatedAt)
                ).stream()
                .map(WorkflowEntityConverter::toDomain)
                .toList();
    }
}
```

使用约束：

- 单表 CRUD 优先使用 `BaseMapper`。
- 条件查询优先使用 `LambdaQueryWrapper`，避免字符串字段名。
- 跨表查询、批量报告查询等复杂 SQL 使用自定义 Mapper/XML。
- Application Service 和 Runtime 不得继承 `ServiceImpl`，避免持久化模型向业务层泄漏。
- JSON 字段通过 Jackson TypeHandler 转换，Entity 标记 `autoResultMap = true`。
- 公共创建时间、更新时间通过 `MetaObjectHandler` 自动填充。
- `BlockAttackInnerInterceptor` 只保护平台元数据库；SQL Step 的安全控制由 SQL Step 自身负责。
- 被测业务数据库查询禁止使用平台 `BaseMapper`，必须通过 `RuntimeDataSourceManager` 和参数化 JDBC 执行。

## 8. 领域模型

```text
Project
  ├── ProjectVariable
  └── WorkflowGroup
        ├── GroupVariable
        ├── BeforeGroupHook
        └── Workflow
              ├── WorkflowVariable
              ├── BeforeWorkflowHook
              ├── WorkflowVersion
              └── StepDefinition

Execution
  ├── GroupExecution
  ├── WorkflowExecution
  ├── HookExecution
  └── StepExecution
        ├── RequestSnapshot
        ├── ResponseSnapshot
        ├── ExtractionResult
        └── AssertionResult
```

测试工程严格按照以下层级组织：

```text
项目（Project）
  ↓
组（WorkflowGroup）
  ↓
工作流（Workflow）
  ↓
执行步骤（Step）
```

约束：

- 一个项目可以包含多个组。
- 一个组只能属于一个项目。
- 一个工作流只能属于一个组。
- 一个工作流包含多个有序执行步骤。
- 组是批量执行单元，按照配置顺序执行组内启用的工作流。
- 一期组内工作流串行执行；并行执行放入二期。

### 8.1 项目与组定义

```java
public record ProjectDefinition(
        String id,
        String name,
        boolean enabled
) {
}
```

```java
public record WorkflowGroupDefinition(
        String id,
        String projectId,
        String name,
        int order,
        boolean enabled,
        HookDefinition beforeHook
) {
}
```

### 8.2 工作流定义

```java
public record WorkflowDefinition(
        String id,
        String groupId,
        String name,
        int order,
        boolean enabled,
        int version,
        HookDefinition beforeHook,
        List<StepDefinition> steps
) {
}
```

### 8.3 步骤定义

```java
public record StepDefinition(
        String id,
        String code,
        String name,
        StepType type,
        boolean enabled,
        int order,
        FailureStrategy failureStrategy,
        RetryPolicy retryPolicy,
        JsonNode config,
        List<ExtractionDefinition> extractions,
        List<AssertionDefinition> assertions
) {
}
```

首版步骤类型：

```java
public enum StepType {
    HTTP,
    SQL,
    DELAY
}
```

预留扩展类型：

```text
SCRIPT、REDIS、MQ、GRPC、CONDITION、LOOP、SUB_WORKFLOW
```

一期只实现线性工作流和组内串行执行，不实现条件分支、循环和并行节点。

### 8.4 钩子定义

钩子复用步骤执行体系，由一组有序的 Hook Step 构成。Hook Step 与普通步骤使用相同的 `StepExecutor`，但在报告中以 `HOOK` 阶段单独展示。

```java
public record HookDefinition(
        String id,
        HookType type,
        boolean enabled,
        List<StepDefinition> steps
) {
}
```

一期支持：

```java
public enum HookType {
    BEFORE_GROUP,
    BEFORE_WORKFLOW
}
```

一期 Hook Step 支持 `HTTP`、`SQL`、`DELAY`。钩子失败默认终止对应组或工作流的执行，并记录完整的 HookExecution。

## 9. Engine 对外 API

UI 只能通过 Engine API 访问功能，不能直接调用 MyBatis-Plus Mapper。

```java
public interface WorkflowService {
    String create(CreateWorkflowCommand command);
    void update(UpdateWorkflowCommand command);
    void delete(String workflowId);
    WorkflowDetail findById(String workflowId);
    List<WorkflowSummary> findByGroupId(String groupId);
    WorkflowValidationResult validate(String workflowId);
    int publish(String workflowId);
}
```

```java
public interface ScopedVariableService {
    List<ScopedVariable> findByScope(ScopeType scopeType, String scopeId);
    String save(SaveScopedVariableCommand command);
    void delete(String variableId);
    EffectiveEnvironmentPreview preview(
            String projectId,
            String groupId,
            String workflowId
    );
}
```

```java
public interface ProjectService {
    String create(CreateProjectCommand command);
    void update(UpdateProjectCommand command);
    void delete(String projectId);
    ProjectDetail findById(String projectId);
    List<ProjectSummary> findAll();
}
```

```java
public interface WorkflowGroupService {
    String create(CreateWorkflowGroupCommand command);
    void update(UpdateWorkflowGroupCommand command);
    void delete(String groupId);
    WorkflowGroupDetail findById(String groupId);
    List<WorkflowGroupSummary> findByProjectId(String projectId);
}
```

```java
public interface WorkflowExecutionService {
    ExecutionHandle submitWorkflow(
            ExecutionCommand command,
            ExecutionListener listener
    );

    ExecutionHandle submitGroup(
            GroupExecutionCommand command,
            ExecutionListener listener
    );

    void cancel(String executionId);
}
```

```java
public interface ExecutionQueryService {
    ExecutionDetail findById(String executionId);
    List<ExecutionSummary> findHistory(ExecutionQuery query);
    List<StepExecutionDetail> findSteps(String executionId);
}
```

```java
public interface RuntimeDataSourceService {
    String save(SaveDataSourceCommand command);
    void delete(String datasourceId);
    List<RuntimeDataSourceSummary> findAll();
    ConnectionTestResult testConnection(String datasourceId);
}
```

## 10. 工作流运行机制

### 10.1 单工作流执行

```text
提交工作流执行命令
    ↓
读取工作流当前定义并生成不可变执行快照
    ↓
合并全局、项目、组、工作流环境变量
    ↓
创建不可变 EnvironmentSnapshot
    ↓
创建 WorkflowExecution 和上下文
    ↓
执行 BeforeWorkflowHook
    ↓
按 sortOrder 顺序执行步骤
    ↓
解析步骤输入变量
    ↓
选择 StepExecutor
    ↓
执行 HTTP/SQL/Delay
    ↓
保存原始输出
    ↓
执行变量提取
    ↓
执行断言
    ↓
更新 ExecutionContext
    ↓
持久化 StepExecution
    ↓
继续、重试或终止
    ↓
汇总并保存最终报告
```

直接执行单个工作流时，不执行 `BeforeGroupHook`；无论工作流是单独运行还是被组运行，都会执行自己的 `BeforeWorkflowHook`。

### 10.2 组执行

```text
提交组执行命令
    ↓
读取项目、组和启用的工作流
    ↓
合并全局、项目、组环境变量
    ↓
创建 GroupExecution 和组上下文
    ↓
执行 BeforeGroupHook（仅一次）
    ↓
按 sortOrder 串行执行组内工作流
    ↓
为每个工作流合并工作流环境变量
    ↓
创建工作流子上下文
    ↓
执行 BeforeWorkflowHook
    ↓
执行工作流步骤
    ↓
汇总 GroupExecution 报告
```

组钩子输出的运行时变量对本次组执行中的所有工作流可见。每个工作流步骤产生的运行时变量默认只在该工作流子上下文内有效，不自动泄漏给同组其他工作流。

执行状态：

```text
PENDING、RUNNING、PASSED、FAILED、CANCELLED、TIMEOUT
```

失败策略：

| 策略 | 行为 |
|---|---|
| STOP | 当前步骤失败后终止工作流 |
| CONTINUE | 记录失败并继续后续步骤，最终工作流失败 |
| IGNORE | 记录异常但不影响工作流最终状态 |

## 11. 步骤扩展机制

```java
public interface StepExecutor {
    StepType supports();

    StepResult execute(
            StepDefinition step,
            ExecutionContext context
    );
}
```

```java
@Component
public class StepExecutorRegistry {
    private final Map<StepType, StepExecutor> executors;

    public StepExecutorRegistry(List<StepExecutor> executorList) {
        this.executors = executorList.stream()
                .collect(Collectors.toMap(
                        StepExecutor::supports,
                        Function.identity()
                ));
    }

    public StepExecutor get(StepType type) {
        StepExecutor executor = executors.get(type);
        if (executor == null) {
            throw new UnsupportedStepTypeException(type);
        }
        return executor;
    }
}
```

新增步骤类型时，新建对应 `StepExecutor` 并注册为 Spring Bean，不修改工作流主执行流程。

## 12. 执行上下文与变量系统

### 12.1 四级环境变量

环境变量按照范围从大到小分为四级，优先级从低到高：

```text
Spring Boot Environment 全局环境变量（最低）
    ↓ 被覆盖
项目环境变量
    ↓ 被覆盖
组环境变量
    ↓ 被覆盖
工作流环境变量（最高）
```

同名变量的最终值按以下公式确定：

```text
effectiveEnvironment = global < project < group < workflow
```

例如：

| 变量 | 全局 | 项目 | 组 | 工作流 | 最终值 |
|---|---|---|---|---|---|
| `baseUrl` | `http://global` | `http://project` | `http://group` | `http://workflow` | `http://workflow` |
| `timeout` | `5000` | `8000` | 未定义 | 未定义 | `8000` |
| `token` | 未定义 | 未定义 | `group-token` | 未定义 | `group-token` |

全局环境变量来自 Spring Boot `Environment`，建议只导入具有以下前缀的属性，避免把操作系统和 JVM 的全部信息暴露给工作流：

```text
workflowtest.global.*
```

例如：

```yaml
workflowtest:
  global:
    connectTimeout: 5000
    readTimeout: 10000
    userAgent: WorkflowTest
```

Spring Boot Environment 内部仍遵循 Spring 的 PropertySource 优先级；Engine 在其基础上提取 `workflowtest.global.*`，去除前缀后作为最低优先级变量层。

### 12.2 环境快照

每次执行开始时生成不可变 `EnvironmentSnapshot`，执行过程中修改项目、组或工作流配置不会影响正在运行的任务。

```java
public record EnvironmentSnapshot(
        Map<String, Object> global,
        Map<String, Object> project,
        Map<String, Object> group,
        Map<String, Object> workflow,
        Map<String, Object> effective
) {
}
```

合并器：

```java
public interface EnvironmentResolver {
    EnvironmentSnapshot resolveForGroup(
            String projectId,
            String groupId
    );

    EnvironmentSnapshot resolveForWorkflow(
            String projectId,
            String groupId,
            String workflowId
    );
}
```

快照既保存最终有效值，也保留每一级来源，便于 UI 解释变量为何取到当前值。敏感变量在快照持久化前加密或脱敏。

### 12.3 环境变量与运行时变量

环境配置和执行过程中产生的数据使用不同命名空间：

```text
${env.baseUrl}       四级环境合并后的只读值
${env.timeout}       四级环境合并后的只读值
${vars.orderId}      钩子或步骤产生的运行时变量
${steps.login...}    指定步骤的结构化执行结果
```

钩子和步骤不能修改持久化的环境变量定义，但可以将提取结果写入本次执行的 `vars`。这样可以防止一次测试运行污染后续运行。

变量必须保留实际类型，不能统一转换为字符串。

支持类型：

```text
String、Integer、Long、BigDecimal、Boolean、LocalDateTime、List、Map、null
```

执行上下文：

```java
public class ExecutionContext {
    private final EnvironmentSnapshot environmentSnapshot;
    private final Map<String, Object> groupVariables;
    private final Map<String, Object> workflowVariables;
    private final Map<String, StepResult> stepResults;
}
```

### 12.4 钩子的变量访问范围

| 执行位置 | 可读取的环境变量 | 可读取的运行时变量 | 输出范围 |
|---|---|---|---|
| BeforeGroupHook | 全局 + 项目 + 组 | 组执行输入 | 写入本次 GroupExecution 的 `groupVariables` |
| BeforeWorkflowHook（单独执行） | 全局 + 项目 + 组 + 工作流 | 工作流执行输入 | 写入本次 WorkflowExecution 的 `workflowVariables` |
| BeforeWorkflowHook（组内执行） | 全局 + 项目 + 组 + 工作流 | 组钩子输出 + 工作流输入 | 写入当前工作流的 `workflowVariables` |
| Workflow Step | 全局 + 项目 + 组 + 工作流 | 组变量 + 工作流变量 + 前序步骤输出 | 写入当前工作流的 `workflowVariables` |

工作流运行时查找 `${vars.key}` 时，优先查找当前 `workflowVariables`，未找到再查找 `groupVariables`。工作流变量不会反向覆盖组变量，也不会传给兄弟工作流。

变量解析规则：

1. 字段值完全由一个变量构成时，保留变量原始类型。
2. 变量嵌入普通字符串时，按字符串拼接处理。
3. 变量不存在且没有默认值时，在步骤执行前直接失败。
4. 日志和报告中的敏感变量必须脱敏。
5. `env` 是只读命名空间，提取目标不得设置为 `env.*`。
6. 钩子和步骤的提取目标只能写入 `vars.*`。

示例：

```json
{
  "orderId": "${vars.orderId}",
  "description": "支付订单 ${vars.orderId}"
}
```

其中 `orderId` 可保持 Long 类型，`description` 为 String 类型。

## 13. HTTP Step

定义示例：

```json
{
  "code": "createOrder",
  "name": "创建订单",
  "type": "HTTP",
  "config": {
    "method": "POST",
    "url": "${env.baseUrl}/orders",
    "headers": {
      "Authorization": "Bearer ${env.token}",
      "Content-Type": "application/json"
    },
    "query": {},
    "bodyType": "JSON",
    "body": {
      "skuId": "${vars.skuId}",
      "quantity": 2
    },
    "connectTimeoutMs": 5000,
    "readTimeoutMs": 10000
  },
  "extractions": [
    {
      "target": "vars.orderId",
      "source": "RESPONSE_BODY",
      "expression": "$.data.orderId",
      "required": true
    }
  ],
  "assertions": [
    {
      "source": "STATUS_CODE",
      "operator": "EQUALS",
      "expected": 200
    }
  ]
}
```

HTTP 结果统一包含：

```text
请求方法、最终 URL、请求头、请求体、响应状态、响应头、响应体、耗时、异常信息
```

以下敏感字段保存前默认脱敏：

```text
Authorization、Cookie、Set-Cookie、password、secret、token、accessToken、refreshToken
```

## 14. SQL Step

SQL Step 通过独立的运行时数据源访问被测业务数据库，不复用平台元数据库的 MyBatis-Plus DataSource，也不通过 `BaseMapper` 执行用户配置的动态 SQL。

```java
public interface RuntimeDataSourceManager {
    DataSource getDataSource(String datasourceId);
    ConnectionTestResult testConnection(String datasourceId);
    void close(String datasourceId);
}
```

SQL 必须使用命名参数绑定：

```sql
SELECT id, status
FROM orders
WHERE id = :orderId
```

步骤配置：

```json
{
  "code": "queryOrder",
  "type": "SQL",
  "config": {
    "datasourceId": "sit-order-db",
    "operation": "QUERY",
    "sql": "SELECT id, status FROM orders WHERE id = :orderId",
    "parameters": {
      "orderId": "${vars.orderId}"
    },
    "maxRows": 100,
    "timeoutSeconds": 10
  }
}
```

首版支持：

- `QUERY`：返回结果集。
- `UPDATE`：执行插入、更新和删除，返回影响行数。

默认禁止 `DROP`、`TRUNCATE`、`ALTER`、`CREATE`。如确有需求，必须在数据源配置中显式开启危险 SQL。

## 15. 提取与断言

首版提取来源：

```text
RESPONSE_BODY、RESPONSE_HEADER、STATUS_CODE、SQL_ROWS、UPDATE_COUNT、CONSTANT
```

首版提取方式：

```text
JSON_PATH、REGEX、HEADER_NAME、DIRECT
```

首版断言操作符：

```text
EQUALS、NOT_EQUALS、CONTAINS、NOT_CONTAINS、EXISTS、NOT_EXISTS、
IS_NULL、NOT_NULL、GREATER_THAN、GREATER_THAN_OR_EQUAL、
LESS_THAN、LESS_THAN_OR_EQUAL、MATCHES_REGEX、SIZE_EQUALS
```

断言结果结构化保存：

```json
{
  "passed": false,
  "source": "RESPONSE_BODY",
  "expression": "$.data.status",
  "operator": "EQUALS",
  "expected": "SUCCESS",
  "actual": "PROCESSING",
  "message": "期望 SUCCESS，实际 PROCESSING"
}
```

## 16. 异步执行、事件和取消

工作流在专用线程池运行，禁止占用 JavaFX Application Thread。

建议初始线程池配置：

```text
corePoolSize = 2
maxPoolSize = 4
queueCapacity = 50
threadNamePrefix = workflow-
```

事件接口：

```java
@FunctionalInterface
public interface ExecutionListener {
    void onEvent(ExecutionEvent event);
}
```

事件类型：

```text
GROUP_EXECUTION_STARTED
EXECUTION_STARTED
HOOK_STARTED
HOOK_PASSED
HOOK_FAILED
STEP_STARTED
STEP_PASSED
STEP_FAILED
EXECUTION_COMPLETED
GROUP_EXECUTION_COMPLETED
EXECUTION_CANCELLED
```

提交执行后返回：

```java
public record ExecutionHandle(
        String executionId,
        CompletableFuture<ExecutionResult> future
) {
}
```

Engine 维护执行控制对象和 `Future`。取消时设置取消标识并尝试中断任务；每个步骤开始前必须检查取消状态。HTTP 和 SQL 操作必须配置超时。

## 17. 数据库设计

### 17.1 核心表

```text
ts_project
ts_workflow_group
ts_workflow
ts_workflow_version（二期启用）
ts_step_definition
ts_scope_variable
ts_hook_definition
ts_hook_step
ts_runtime_datasource
ts_group_execution
ts_execution
ts_hook_execution
ts_step_execution
ts_assertion_result
```

### 17.2 关键设计原则

- 主键使用 UUID 字符串，避免依赖数据库自增策略。
- 结构稳定且需要查询的字段使用普通列。
- 步骤配置、提取配置、断言配置使用 JSON 文本保存。
- 项目、组、工作流通过外键形成严格的树形归属关系。
- 项目、组、工作流变量统一保存在 `ts_scope_variable`，通过 `scope_type + scope_id` 区分范围。
- Spring Boot 全局环境变量不写入数据库，只在执行开始时读取并生成快照。
- 钩子定义与普通执行步骤分开存储，但运行时复用相同的 StepExecutor。
- 一期每次执行时保存不可变工作流快照，保证报告可追溯。
- 二期增加显式发布、版本对比和回滚后，历史执行同时绑定具体版本。
- 时间统一按 UTC 存储，展示时转换为本地时区。
- SQLite 中布尔值映射为 0/1，由 TypeHandler 屏蔽差异。
- Entity 使用 MyBatis-Plus 注解描述表映射，领域对象不添加持久化注解。
- Repository 接口定义在领域边界，实现类内部组合 `BaseMapper`。
- 分页查询对外返回项目自有的分页 DTO，不向 Desktop 暴露 MyBatis-Plus `IPage`。

### 17.3 主要表结构示例

```sql
CREATE TABLE ts_project (
    id              VARCHAR(36) PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    enabled         INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE TABLE ts_workflow_group (
    id              VARCHAR(36) PRIMARY KEY,
    project_id      VARCHAR(36) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    sort_order      INTEGER NOT NULL,
    enabled         INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    UNIQUE(project_id, name)
);

CREATE TABLE ts_workflow (
    id              VARCHAR(36) PRIMARY KEY,
    group_id        VARCHAR(36) NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    sort_order      INTEGER NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 0,
    enabled         INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    UNIQUE(group_id, name)
);

CREATE TABLE ts_scope_variable (
    id              VARCHAR(36) PRIMARY KEY,
    scope_type      VARCHAR(30) NOT NULL,
    scope_id        VARCHAR(36) NOT NULL,
    variable_key    VARCHAR(200) NOT NULL,
    value_type      VARCHAR(30) NOT NULL,
    value_json      TEXT,
    sensitive       INTEGER NOT NULL DEFAULT 0,
    enabled         INTEGER NOT NULL DEFAULT 1,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,
    UNIQUE(scope_type, scope_id, variable_key)
);

CREATE TABLE ts_hook_definition (
    id              VARCHAR(36) PRIMARY KEY,
    owner_type      VARCHAR(30) NOT NULL,
    owner_id        VARCHAR(36) NOT NULL,
    hook_type       VARCHAR(30) NOT NULL,
    enabled         INTEGER NOT NULL DEFAULT 1,
    failure_strategy VARCHAR(30) NOT NULL DEFAULT 'STOP',
    UNIQUE(owner_type, owner_id, hook_type)
);

CREATE TABLE ts_hook_step (
    id                  VARCHAR(36) PRIMARY KEY,
    hook_id             VARCHAR(36) NOT NULL,
    step_code           VARCHAR(100) NOT NULL,
    step_name           VARCHAR(200) NOT NULL,
    step_type           VARCHAR(50) NOT NULL,
    sort_order          INTEGER NOT NULL,
    enabled             INTEGER NOT NULL DEFAULT 1,
    config_json         TEXT NOT NULL,
    extraction_json     TEXT,
    assertion_json      TEXT,
    retry_json          TEXT,
    UNIQUE(hook_id, step_code)
);

CREATE TABLE ts_workflow_version (
    id              VARCHAR(36) PRIMARY KEY,
    workflow_id     VARCHAR(36) NOT NULL,
    version         INTEGER NOT NULL,
    snapshot_json   TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    UNIQUE(workflow_id, version)
);

-- 一期执行依赖 workflow_snapshot；二期启用显式发布后使用本表和 workflow_version。

CREATE TABLE ts_step_definition (
    id                  VARCHAR(36) PRIMARY KEY,
    workflow_id         VARCHAR(36) NOT NULL,
    step_code           VARCHAR(100) NOT NULL,
    step_name           VARCHAR(200) NOT NULL,
    step_type           VARCHAR(50) NOT NULL,
    sort_order          INTEGER NOT NULL,
    enabled             INTEGER NOT NULL DEFAULT 1,
    config_json         TEXT NOT NULL,
    extraction_json     TEXT,
    assertion_json      TEXT,
    failure_strategy    VARCHAR(30) NOT NULL,
    retry_json          TEXT,
    UNIQUE(workflow_id, step_code)
);

CREATE TABLE ts_execution (
    id                  VARCHAR(36) PRIMARY KEY,
    group_execution_id  VARCHAR(36),
    workflow_id         VARCHAR(36) NOT NULL,
    workflow_version    INTEGER,
    status              VARCHAR(30) NOT NULL,
    input_json          TEXT,
    environment_snapshot TEXT NOT NULL,
    context_snapshot    TEXT,
    workflow_snapshot   TEXT NOT NULL,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    elapsed_ms          BIGINT,
    error_message       TEXT
);

CREATE TABLE ts_group_execution (
    id                  VARCHAR(36) PRIMARY KEY,
    project_id          VARCHAR(36) NOT NULL,
    group_id            VARCHAR(36) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    input_json          TEXT,
    environment_snapshot TEXT NOT NULL,
    context_snapshot    TEXT,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    elapsed_ms          BIGINT,
    error_message       TEXT
);

CREATE TABLE ts_hook_execution (
    id                  VARCHAR(36) PRIMARY KEY,
    group_execution_id  VARCHAR(36),
    workflow_execution_id VARCHAR(36),
    hook_id             VARCHAR(36) NOT NULL,
    hook_type           VARCHAR(30) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    output_json         TEXT,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    elapsed_ms          BIGINT,
    error_message       TEXT
);

CREATE TABLE ts_step_execution (
    id                  VARCHAR(36) PRIMARY KEY,
    execution_id        VARCHAR(36),
    hook_execution_id   VARCHAR(36),
    step_id             VARCHAR(36) NOT NULL,
    step_code           VARCHAR(100) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    request_json        TEXT,
    response_json       TEXT,
    output_json         TEXT,
    extracted_json      TEXT,
    started_at          TIMESTAMP,
    finished_at         TIMESTAMP,
    elapsed_ms          BIGINT,
    error_message       TEXT
);
```

## 18. 平台数据库可替换策略

默认使用 SQLite，配置示例：

```yaml
spring:
  datasource:
    url: jdbc:sqlite:${workflowtest.data-dir}/workflow-test.db
    driver-class-name: org.sqlite.JDBC
```

切换 MySQL 时：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/workflow_test
    username: workflow_test
    password: ${WORKFLOW_TEST_DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

兼容要求：

- Mapper 和自定义 XML 中避免使用 SQLite 专有函数。
- JSON 使用 TEXT 保存，不强依赖 JSON/JSONB 字段。
- 常规 CRUD 和条件查询使用 MyBatis-Plus `BaseMapper`、`LambdaQueryWrapper`。
- 分页通过 MyBatis-Plus 分页插件和当前数据库 `DbType` 处理。
- 少量复杂 SQL 使用自定义 Mapper/XML，并通过 `databaseIdProvider` 隔离方言差异。
- Flyway 脚本按 `common/sqlite/mysql/postgresql` 管理。
- 第一版只验收 SQLite；MySQL、PostgreSQL 作为架构预留能力。

SQLite 适合单机、低并发的桌面工具。如果未来改为多人共享服务，应切换 MySQL 或 PostgreSQL，并重新评估部署架构。

## 19. 安全要求

- 数据源密码使用 AES-GCM 加密后存储。
- 加密主密钥保存在当前 Windows 用户范围内，优先使用 Windows DPAPI 保护。
- 请求、响应和变量日志执行统一脱敏。
- SQL 参数必须绑定，禁止直接拼接变量。
- 危险 SQL 默认关闭。
- 报告导出前再次执行敏感字段检查。
- SQLite 数据库和日志目录只授予当前用户访问权限。

---

# 第二部分：WorkflowTestDesktop

## 20. 项目定位

`WorkflowTestDesktop` 是面向测试人员的 JavaFX 桌面客户端。它负责配置和展示，不实现工作流执行规则，也不直接访问 SQLite 或 MyBatis-Plus Mapper。

Desktop 的所有业务操作通过 `WorkflowTestEngine` 暴露的 Java API 完成。

## 21. 推荐目录结构

```text
WorkflowTestDesktop/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/example/workflowtest/desktop/
    │   │   ├── launcher/
    │   │   │   ├── DesktopLauncher.java
    │   │   │   └── WorkflowTestApplication.java
    │   │   ├── config/
    │   │   ├── view/
    │   │   ├── controller/
    │   │   ├── viewmodel/
    │   │   ├── component/
    │   │   ├── navigation/
    │   │   ├── dialog/
    │   │   ├── task/
    │   │   └── util/
    │   └── resources/
    │       ├── fxml/
    │       ├── css/
    │       ├── icons/
    │       └── application.yml
    └── test/
```

## 22. Spring 与 JavaFX 集成

桌面程序以非 Web 模式创建 Spring 容器：

```java
public class WorkflowTestApplication extends Application {
    private ConfigurableApplicationContext context;

    @Override
    public void init() {
        context = new SpringApplicationBuilder(DesktopBootConfiguration.class)
                .web(WebApplicationType.NONE)
                .headless(false)
                .run();
    }

    @Override
    public void start(Stage stage) {
        context.getBean(MainWindow.class).show(stage);
    }

    @Override
    public void stop() {
        context.close();
        Platform.exit();
    }
}
```

启动入口：

```java
public final class DesktopLauncher {
    public static void main(String[] args) {
        Application.launch(WorkflowTestApplication.class, args);
    }
}
```

## 23. UI 架构

采用简化 MVVM：

```text
FXML / View
    ↓ 绑定和事件
ViewModel
    ↓
Desktop Application Service
    ↓
WorkflowTestEngine API
```

职责约束：

| 层 | 职责 |
|---|---|
| View/FXML | 页面结构、样式、控件 |
| Controller | JavaFX 生命周期、用户事件转发 |
| ViewModel | 页面状态、校验、命令和属性绑定 |
| Desktop Service | 页面流程编排、错误转换、后台任务调用 |
| Engine API | 核心业务和持久化 |

Controller 中禁止编写 SQL、HTTP 请求和工作流执行逻辑。

## 24. 页面规划

### 24.1 主界面

```text
┌────────────────────────────────────────────────────┐
│ WorkflowTest                                        │
├──────────────┬─────────────────────────────────────┤
│ 测试工程      │                                     │
│ 环境变量      │          当前功能页面                │
│ 数据源        │                                     │
│ 执行历史      │                                     │
│ 设置          │                                     │
└──────────────┴─────────────────────────────────────┘
```

测试工程使用树形导航：

```text
订单系统项目
├── 订单主流程组
│   ├── 创建订单工作流
│   │   ├── 登录
│   │   ├── 创建订单
│   │   └── 查询订单
│   └── 订单支付工作流
└── 订单异常场景组
```

项目和组节点均提供环境变量入口；组节点额外提供“执行整个组”和“组前置钩子”入口。

### 24.2 工作流设计器

```text
┌──────────────┬───────────────────────┬──────────────┐
│ 步骤列表      │ 当前步骤配置           │ 可用变量      │
│              │                       │              │
│ 前置钩子      │ 类型 / 基础配置         │ env（含来源）  │
│ 1 查询订单    │ 请求或 SQL             │ vars         │
│ 2 支付订单    │ 请求或 SQL             │ vars         │
│ 3 检查结果    │ 提取 / 断言 / 重试      │ steps        │
│              │                       │              │
│ ＋新增步骤    │                       │              │
└──────────────┴───────────────────────┴──────────────┘
```

首版使用步骤列表配合新增、删除、复制、上移和下移，不开发自由拖拽流程画布。

### 24.3 环境变量管理

功能包括：

- 分别查看全局、项目、组、工作流四级变量。
- 全局变量来自 Spring Boot Environment，在 UI 中默认只读。
- 在当前项目、组或工作流范围新增、编辑和删除变量。
- 显示变量的定义层级、覆盖关系和最终有效值。
- 将变量标记为敏感变量。
- 导入、导出非敏感配置。

变量预览示例：

| 变量 | 最终值 | 生效来源 | 被覆盖来源 |
|---|---|---|---|
| `baseUrl` | `http://workflow` | 工作流 | 全局、项目、组 |
| `timeout` | `8000` | 项目 | 全局 |

### 24.4 钩子管理

- 组页面配置 `BeforeGroupHook`。
- 工作流页面配置 `BeforeWorkflowHook`。
- 钩子编辑器复用普通步骤编辑器。
- UI 明确展示当前钩子可以读取的环境变量范围。
- 调试钩子时使用临时执行上下文，不回写环境变量配置。

### 24.5 数据源管理

功能包括：

- 数据库类型、JDBC URL、用户名、密码配置。
- 连接测试。
- 连接超时和查询超时设置。
- 危险 SQL 开关。
- 查看数据源当前状态，但不展示明文密码。

### 24.6 执行中心

```text
┌────────────────────────────────────────────────────┐
│ 订单支付流程/订单主流程组         [运行] [停止]         │
├───────────────────┬────────────────────────────────┤
│ 步骤状态           │ 当前步骤详情                    │
│                   │                                │
│ ✓ 前置钩子  20ms   │ 环境快照 / 钩子输出             │
│ ✓ 查询订单  32ms   │ 请求 / 响应 / SQL结果           │
│ ✓ 支付订单 126ms   │ 提取变量 / 断言                 │
│ ✗ 查询结果  18ms   │ 错误信息                        │
└───────────────────┴────────────────────────────────┘
```

### 24.7 执行历史与报告

支持按项目、组、工作流、执行类型、状态和时间筛选，详情展示：

```text
执行摘要
环境变量合并快照及变量来源
组钩子和工作流钩子执行结果
工作流版本和快照
步骤执行时间线
HTTP 请求与响应
SQL 和结果集
提取变量
断言结果
错误堆栈摘要
```

## 25. JavaFX 线程模型

JavaFX Application Thread 只负责 UI 操作。Engine 的执行、HTTP、SQL 和数据库持久化均在后台线程执行。

```text
用户点击运行
    ↓
ViewModel 调用 Engine.submit
    ↓
Engine 工作线程执行
    ↓
ExecutionListener 收到事件
    ↓
Platform.runLater(...)
    ↓
更新 JavaFX ObservableProperty
```

示例：

```java
executionService.submit(command, event ->
        Platform.runLater(() -> executionViewModel.onEvent(event))
);
```

任何 Controller 或 ViewModel 都不能在 JavaFX Application Thread 中执行阻塞式 HTTP、SQL 或大文件操作。

## 26. 本地文件布局

Windows 默认目录：

```text
%LOCALAPPDATA%\WorkflowTest\
├── data\
│   └── workflow-test.db
├── logs\
├── reports\
├── backups\
└── temp\

%APPDATA%\WorkflowTest\
└── application.yml
```

数据库和用户数据不能放在程序安装目录，避免权限问题以及升级、卸载时误删除。

## 27. 桌面打包

使用 `jpackage` 生成带运行时的 Windows 安装包：

```text
WorkflowTest-1.0.0.exe
```

安装内容：

```text
WorkflowTest/
├── WorkflowTest.exe
├── runtime/
├── app/
│   ├── workflow-test-desktop.jar
│   ├── workflow-test-engine.jar
│   └── dependencies/
└── legal/
```

用户不需要预先安装 JDK/JRE。升级程序时保留 `%LOCALAPPDATA%` 和 `%APPDATA%` 中的数据。

---

# 第三部分：两个项目的集成约定

## 28. 依赖方向

```text
WorkflowTestDesktop
        ↓
WorkflowTestEngine API
        ↓
Engine Application/Domain
        ↓
Engine Persistence/Executor
```

禁止出现：

```text
WorkflowTestEngine → WorkflowTestDesktop
Desktop Controller → MyBatis-Plus Mapper
Desktop ViewModel → SQLite
Engine Runtime → JavaFX
```

## 29. DTO 与异常边界

Engine 对外暴露稳定 DTO，不向 Desktop 暴露数据库 Entity、Mapper 或 JDBC 对象。

Engine 异常建议分为：

```text
ValidationException
WorkflowNotFoundException
WorkflowGroupNotFoundException
ProjectNotFoundException
ScopedVariableNotFoundException
VariableResolveException
StepExecutionException
AssertionFailedException
DataSourceConnectionException
ExecutionCancelledException
```

Desktop 将异常映射成用户可理解的消息，同时将完整异常写入本地日志。

## 30. 版本管理

- Desktop 和 Engine 分别维护版本。
- Engine 遵循语义化版本。
- Engine API 出现不兼容变更时升级主版本。
- Desktop 构建时锁定 Engine 版本，不使用动态版本。
- 安装包同时记录 Desktop、Engine 和数据库 Schema 版本。

## 31. 完整业务示例

```text
Project：订单系统
  Group：订单主流程
    BeforeGroupHook：获取管理员 Token
    Workflow：订单支付流程
      BeforeWorkflowHook：准备待支付订单
      Step 1：调用支付接口
      Step 2：查询支付结果
```

变量合并：

```text
Spring Environment：baseUrl、connectTimeout
Project：tenantId、databaseSchema
Group：operator、paymentChannel
Workflow：currency
```

`BeforeGroupHook` 可以读取全局、项目、组变量，并将 Token 提取到 `vars.adminToken`。`BeforeWorkflowHook` 可以继续读取工作流变量和 `vars.adminToken`，并输出 `vars.orderId`。后续步骤可以同时访问 `${env.currency}`、`${vars.adminToken}` 和 `${vars.orderId}`。

BeforeWorkflowHook 中的 SQL Step：

```json
{
  "code": "queryOrder",
  "type": "SQL",
  "config": {
    "datasourceId": "sit-order-db",
    "operation": "QUERY",
    "sql": "SELECT id FROM orders WHERE status = :status LIMIT 1",
    "parameters": {
      "status": "WAIT_PAY"
    }
  },
  "extractions": [
    {
      "target": "vars.orderId",
      "source": "SQL_ROWS",
      "expression": "$[0].id",
      "required": true
    }
  ]
}
```

HTTP Step：

```json
{
  "code": "payOrder",
  "type": "HTTP",
  "config": {
    "method": "POST",
    "url": "${env.baseUrl}/orders/${vars.orderId}/pay",
    "headers": {
      "Authorization": "Bearer ${vars.adminToken}"
    }
  },
  "extractions": [
    {
      "target": "vars.paymentId",
      "source": "RESPONSE_BODY",
      "expression": "$.data.paymentId"
    }
  ],
  "assertions": [
    {
      "source": "STATUS_CODE",
      "operator": "EQUALS",
      "expected": 200
    }
  ]
}
```

## 32. 测试策略

### 32.1 WorkflowTestEngine

| 测试类型 | 覆盖内容 |
|---|---|
| 单元测试 | 四级变量覆盖、钩子作用域、提取、断言、重试、状态流转 |
| Mapper 测试 | SQLite 表结构、BaseMapper CRUD、分页、条件查询、JSON TypeHandler |
| HTTP 集成测试 | MockWebServer/WireMock 请求与响应 |
| SQL 集成测试 | SQLite 及 Testcontainers 数据库 |
| 工作流集成测试 | 组钩子 → 工作流钩子 → SQL/HTTP 步骤完整变量传递 |
| 兼容性测试 | 数据库迁移和旧版本升级 |

### 32.2 WorkflowTestDesktop

| 测试类型 | 覆盖内容 |
|---|---|
| ViewModel 单元测试 | 页面状态、校验、命令可用性 |
| UI 组件测试 | 表单输入、步骤排序、运行状态展示 |
| 集成测试 | Desktop 调用真实 Engine API |
| 打包验证 | 全新 Windows 环境安装、启动、升级、卸载 |

## 33. 一期建设范围

一期目标是完成可以投入个人和小范围内部使用的桌面自动化测试工具。

### 33.1 WorkflowTestEngine 一期

- SQLite、Flyway 和 MyBatis-Plus 基础持久化。
- 项目 → 组 → 工作流 → 执行步骤四级组织模型。
- 项目、组、工作流 CRUD 和排序。
- Spring Environment 全局变量读取。
- 项目、组、工作流环境变量管理。
- 四级变量合并、覆盖、类型保留和执行快照。
- `BeforeGroupHook` 和 `BeforeWorkflowHook`。
- HTTP、SQL、Delay Step。
- JSONPath 提取和基础断言。
- HTTP/SQL 跨步骤变量传递。
- 组内工作流串行执行。
- 后台执行、停止、超时、失败策略和基础重试。
- 工作流、钩子、步骤执行记录。
- 敏感变量和数据源密码保护。

### 33.2 WorkflowTestDesktop 一期

- JavaFX 与 Spring 非 Web 容器集成。
- 测试工程树：项目、组、工作流、步骤。
- 四级环境变量查看、编辑和有效值预览。
- 组前置钩子和工作流前置钩子编辑器。
- HTTP、SQL、Delay Step 编辑器。
- 单工作流运行和整组运行。
- 实时执行状态、停止执行和基础报告。
- Windows 本地数据目录、备份和 `jpackage` 安装包。

### 33.3 一期验收标准

1. 可以按照项目、组、工作流、执行步骤组织测试工程。
2. 可以调整组内工作流顺序和工作流内步骤顺序。
3. 同名环境变量严格按全局、项目、组、工作流从低到高覆盖。
4. 可以查看最终有效变量及其来源，敏感值不能明文展示。
5. 组运行前只执行一次 `BeforeGroupHook`。
6. 每个工作流运行前执行一次 `BeforeWorkflowHook`。
7. 组钩子可以访问全局、项目和组变量。
8. 工作流钩子可以访问全局、项目、组、工作流变量；组内执行时还可以访问组钩子输出。
9. 钩子输出只影响本次执行，不回写环境变量配置。
10. 工作流支持多个有序 HTTP、SQL、Delay 步骤。
11. 上一步 HTTP 或 SQL 输出可以作为后续步骤输入。
12. 可以单独执行工作流，也可以串行执行整个组。
13. 执行期间 UI 不冻结，并且可以停止任务。
14. 报告可以区分组钩子、工作流钩子和普通步骤。
15. Windows 安装后无需用户安装 Java 或数据库。
16. Engine 不依赖 JavaFX，Desktop 不直接依赖 Mapper。

## 34. 二期建设范围

二期在一期稳定模型上增加复杂编排、复用和工程化能力：

- `AfterWorkflowHook`、`AfterGroupHook`，并支持成功、失败、始终执行策略。
- 条件分支、循环、并行步骤和子工作流。
- 组内工作流并行执行及最大并发数控制。
- 工作流发布、版本快照、版本对比和回滚。
- JavaScript/Groovy 受限脚本步骤。
- Redis、MQ、gRPC 等扩展步骤。
- 测试数据集和参数化批量执行。
- 定时任务和失败重跑。
- JUnit XML、HTML 等报告导出。
- 工作流、组和项目的导入导出。
- 数据备份、恢复和数据库迁移工具。
- MySQL/PostgreSQL 平台元数据库正式兼容验证。
- 插件化 StepExecutor 和扩展包管理。

## 35. 非目标

当前桌面产品不规划以下能力：

- Spring MVC、REST API 和远程调用。
- 多人实时协作与服务端权限系统。
- 分布式任务调度。
- 云端同步。

自由拖拽流程画布不作为一期目标，可在二期根据实际使用反馈决定是否建设。

---

## 36. 一期实现状态

一期已按本方案落地为 `WorkflowTestEngine` 与 `WorkflowTestDesktop` 两个独立 Maven 项目，并由根工程统一构建。

已完成：

- 项目、组、工作流、普通步骤和前置钩子步骤的完整增删改查。
- 组、工作流、普通步骤和钩子步骤的顺序调整。
- Spring Environment、项目、组、工作流四级变量合并及来源预览。
- 敏感变量和数据源密码的 AES-GCM 本地加密，界面及执行快照脱敏。
- `BeforeGroupHook`、`BeforeWorkflowHook` 及本次执行上下文传递。
- HTTP、SQL、Delay 步骤，JSONPath 提取、基础断言、重试、失败策略与取消。
- 单工作流与整组异步串行执行、实时事件日志和执行历史。
- 历史详情按 `BEFORE_GROUP`、`BEFORE_WORKFLOW`、`WORKFLOW` 区分阶段。
- 数据源新增、编辑、删除和连接测试。
- SQLite 在线一致性备份及退出程序后的文件恢复流程。
- JDK 21 `jpackage` 便携应用目录及 EXE 安装包脚本。

自动化集成测试覆盖 Flyway 迁移、MyBatis-Plus CRUD、变量覆盖、敏感变量脱敏、两级前置钩子、组串行执行、HTTP 请求、SQL 命名参数查询、排序、历史阶段和数据库备份。Windows 便携应用已完成启动冒烟验证。

## 37. 结论

WorkflowTest 采用“独立 Engine JAR + JavaFX Desktop”的桌面架构。两个项目职责清晰、构建独立，并在同一 JVM 内通过 Java API 集成。Spring Boot 仅作为非 Web IoC 容器，MyBatis-Plus 和 SQLite 负责本地元数据持久化，HTTP/SQL Step 及类型化执行上下文构成核心工作流能力。

该方案优先保证单机桌面工具的安装简单、运行稳定和数据可追溯，同时通过 Engine API、StepExecutor 和数据库兼容约定，为后续增加新步骤类型、替换元数据库或增加其他客户端保留扩展空间。

