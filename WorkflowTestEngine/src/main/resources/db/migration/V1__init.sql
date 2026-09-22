-- =============================================================================
-- WorkflowTest 本地 SQLite 元数据与执行记录表（主键均为自增 BIGINT）
-- 说明：SQLite 使用 INTEGER PRIMARY KEY AUTOINCREMENT 实现 64 位自增主键
-- =============================================================================

-- 测试项目：顶层资产容器
CREATE TABLE IF NOT EXISTS ts_project (
    id          INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    name        VARCHAR(200) NOT NULL,             -- 项目名称
    description TEXT,                              -- 项目说明
    enabled     INTEGER NOT NULL DEFAULT 1,        -- 是否启用（1=是，0=否）
    created_at  DATETIME NOT NULL,                 -- 创建时间
    updated_at  DATETIME NOT NULL                  -- 更新时间
);

-- 工作流组：隶属于项目，按 sort_order 排序与串行执行
CREATE TABLE IF NOT EXISTS ts_workflow_group (
    id          INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    project_id  INTEGER NOT NULL,                  -- 所属项目主键
    name        VARCHAR(200) NOT NULL,             -- 组名称
    description TEXT,                              -- 组说明
    sort_order  INTEGER NOT NULL DEFAULT 0,        -- 显示与执行顺序（升序）
    enabled     INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    created_at  DATETIME NOT NULL,                 -- 创建时间
    updated_at  DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(project_id, name)
);

-- 工作流：隶属于组
CREATE TABLE IF NOT EXISTS ts_workflow (
    id          INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    group_id    INTEGER NOT NULL,                  -- 所属组主键
    name        VARCHAR(200) NOT NULL,             -- 工作流名称
    description TEXT,                              -- 工作流说明
    sort_order  INTEGER NOT NULL DEFAULT 0,        -- 显示与执行顺序（升序）
    enabled     INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    created_at  DATETIME NOT NULL,                 -- 创建时间
    updated_at  DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(group_id, name)
);

-- 工作流步骤：HTTP / SQL / DELAY 等类型及 JSON 配置
CREATE TABLE IF NOT EXISTS ts_step_definition (
    id               INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    workflow_id      INTEGER NOT NULL,                  -- 所属工作流主键
    step_code        VARCHAR(100) NOT NULL,             -- 步骤编码（工作流内唯一）
    step_name        VARCHAR(200) NOT NULL,             -- 步骤名称
    step_type        VARCHAR(30) NOT NULL,              -- 步骤类型（HTTP/SQL/DELAY）
    sort_order       INTEGER NOT NULL DEFAULT 0,        -- 执行顺序（升序）
    enabled          INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    config_json      TEXT NOT NULL,                     -- 步骤配置 JSON
    extraction_json  TEXT,                              -- 变量提取规则 JSON 数组
    assertion_json   TEXT,                              -- 断言规则 JSON 数组
    created_at       DATETIME NOT NULL,                 -- 创建时间
    updated_at       DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(workflow_id, step_code)
);

-- 全局环境变量：持久化存储，运行时通过 global.变量名 引用
CREATE TABLE IF NOT EXISTS ts_global_variable (
    id           INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    variable_key VARCHAR(200) NOT NULL,             -- 变量名（全局唯一）
    value_type   VARCHAR(30) NOT NULL,              -- 值类型（AUTO 等）
    value_json   TEXT,                              -- 变量值 JSON
    enabled      INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    created_at   DATETIME NOT NULL,                 -- 创建时间
    updated_at   DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(variable_key)
);

-- 分层环境变量：scope_type + scope_id 标识 PROJECT/GROUP/WORKFLOW 作用域
CREATE TABLE IF NOT EXISTS ts_scope_variable (
    id           INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    scope_type   VARCHAR(30) NOT NULL,              -- 作用域类型（PROJECT/GROUP/WORKFLOW）
    scope_id     INTEGER NOT NULL,                  -- 作用域主键（对应项目/组/工作流 id）
    variable_key VARCHAR(200) NOT NULL,             -- 变量名
    value_type   VARCHAR(30) NOT NULL,              -- 值类型（AUTO 等）
    value_json   TEXT,                              -- 变量值 JSON
    enabled      INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    created_at   DATETIME NOT NULL,                 -- 创建时间
    updated_at   DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(scope_type, scope_id, variable_key)
);

-- 组级钩子：组前置 / 组后置
CREATE TABLE IF NOT EXISTS ts_hook_definition (
    id               INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    group_id         INTEGER NOT NULL,                  -- 所属组主键
    hook_type        VARCHAR(30) NOT NULL,              -- 钩子类型（BEFORE_GROUP/AFTER_GROUP）
    enabled          INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    created_at       DATETIME NOT NULL,                 -- 创建时间
    updated_at       DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(group_id, hook_type)
);

-- 钩子步骤：语义与工作流步骤一致
CREATE TABLE IF NOT EXISTS ts_hook_step (
    id               INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    hook_id          INTEGER NOT NULL,                  -- 所属钩子主键
    step_code        VARCHAR(100) NOT NULL,             -- 步骤编码（钩子内唯一）
    step_name        VARCHAR(200) NOT NULL,             -- 步骤名称
    step_type        VARCHAR(30) NOT NULL,              -- 步骤类型
    sort_order       INTEGER NOT NULL DEFAULT 0,        -- 执行顺序
    enabled          INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    config_json      TEXT NOT NULL,                     -- 步骤配置 JSON
    extraction_json  TEXT,                              -- 提取规则 JSON
    assertion_json   TEXT,                              -- 断言规则 JSON
    created_at       DATETIME NOT NULL,                 -- 创建时间
    updated_at       DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(hook_id, step_code)
);

-- 项目资源：按 resource_type 区分（DATASOURCE / FILE 等），配置存 config_json
CREATE TABLE IF NOT EXISTS ts_project_resource (
    id            INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    project_id    INTEGER NOT NULL,                  -- 所属项目主键
    resource_type VARCHAR(30) NOT NULL,              -- 资源类型（DATASOURCE / FILE）
    name          VARCHAR(200) NOT NULL,             -- 资源名称（项目内唯一）
    config_json   TEXT NOT NULL,                     -- 类型相关配置 JSON
    enabled       INTEGER NOT NULL DEFAULT 1,        -- 是否启用
    created_at    DATETIME NOT NULL,                 -- 创建时间
    updated_at    DATETIME NOT NULL,                 -- 更新时间
    UNIQUE(project_id, name)
);

-- 组级执行记录
CREATE TABLE IF NOT EXISTS ts_group_execution (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    project_id           INTEGER NOT NULL,                  -- 项目主键
    group_id             INTEGER NOT NULL,                  -- 组主键
    status               VARCHAR(30) NOT NULL,              -- 执行状态
    input_json           TEXT,                              -- 输入参数 JSON
    environment_snapshot TEXT NOT NULL,                     -- 环境变量快照 JSON
    context_snapshot     TEXT,                              -- 组级运行时变量快照 JSON
    started_at           TEXT,                              -- 开始时间
    finished_at          TEXT,                              -- 结束时间
    elapsed_ms           INTEGER,                           -- 耗时（毫秒）
    error_message        TEXT                               -- 错误摘要
);

-- 工作流级执行记录
CREATE TABLE IF NOT EXISTS ts_execution (
    id                   INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    group_execution_id   INTEGER,                           -- 所属组执行主键（独立运行时为 NULL）
    workflow_id          INTEGER NOT NULL,                  -- 工作流主键
    status               VARCHAR(30) NOT NULL,              -- 执行状态
    input_json           TEXT,                              -- 输入参数 JSON
    environment_snapshot TEXT NOT NULL,                     -- 环境变量快照 JSON
    context_snapshot     TEXT,                              -- 工作流运行时变量快照 JSON
    workflow_snapshot    TEXT NOT NULL,                     -- 执行时工作流定义快照 JSON
    started_at           TEXT,                              -- 开始时间
    finished_at          TEXT,                              -- 结束时间
    elapsed_ms           INTEGER,                           -- 耗时（毫秒）
    error_message        TEXT                               -- 错误摘要
);

-- 钩子执行记录
CREATE TABLE IF NOT EXISTS ts_hook_execution (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    group_execution_id    INTEGER,                           -- 组执行主键
    workflow_execution_id INTEGER,                           -- 工作流执行主键
    hook_id               INTEGER NOT NULL,                  -- 钩子定义主键
    hook_type             VARCHAR(30) NOT NULL,              -- 钩子类型
    status                VARCHAR(30) NOT NULL,              -- 执行状态
    output_json           TEXT,                              -- 钩子输出 JSON
    started_at            TEXT,                              -- 开始时间
    finished_at           TEXT,                              -- 结束时间
    elapsed_ms            INTEGER,                           -- 耗时（毫秒）
    error_message         TEXT                               -- 错误摘要
);

-- 步骤执行记录
CREATE TABLE IF NOT EXISTS ts_step_execution (
    id                INTEGER PRIMARY KEY AUTOINCREMENT, -- 主键
    execution_id      INTEGER,                           -- 工作流执行主键
    hook_execution_id INTEGER,                           -- 钩子执行主键
    step_id           INTEGER NOT NULL,                  -- 步骤定义主键
    step_code         VARCHAR(100) NOT NULL,             -- 步骤编码
    phase             VARCHAR(30) NOT NULL DEFAULT 'WORKFLOW', -- 执行阶段
    status            VARCHAR(30) NOT NULL,              -- 执行状态
    request_json      TEXT,                              -- 请求快照 JSON
    response_json     TEXT,                              -- 响应快照 JSON
    output_json       TEXT,                              -- 输出 JSON
    extracted_json    TEXT,                              -- 提取结果 JSON
    assertion_json    TEXT,                              -- 断言结果 JSON
    started_at        TEXT,                              -- 开始时间
    finished_at       TEXT,                              -- 结束时间
    elapsed_ms        INTEGER,                           -- 耗时（毫秒）
    error_message     TEXT                               -- 错误摘要
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_group_project ON ts_workflow_group(project_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_workflow_group ON ts_workflow(group_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_step_workflow ON ts_step_definition(workflow_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_global_variable_key ON ts_global_variable(variable_key);
CREATE INDEX IF NOT EXISTS idx_project_resource ON ts_project_resource(project_id, resource_type);
CREATE INDEX IF NOT EXISTS idx_variable_scope ON ts_scope_variable(scope_type, scope_id);
CREATE INDEX IF NOT EXISTS idx_hook_group ON ts_hook_definition(group_id, hook_type);
CREATE INDEX IF NOT EXISTS idx_execution_workflow ON ts_execution(workflow_id, started_at);
