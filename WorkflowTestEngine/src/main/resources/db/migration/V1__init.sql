CREATE TABLE IF NOT EXISTS wt_project (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS wt_workflow_group (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(project_id, name),
    FOREIGN KEY(project_id) REFERENCES wt_project(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS wt_workflow (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(group_id, name),
    FOREIGN KEY(group_id) REFERENCES wt_workflow_group(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS wt_step_definition (
    id VARCHAR(36) PRIMARY KEY,
    workflow_id VARCHAR(36) NOT NULL,
    step_code VARCHAR(100) NOT NULL,
    step_name VARCHAR(200) NOT NULL,
    step_type VARCHAR(30) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    config_json TEXT NOT NULL,
    extraction_json TEXT,
    assertion_json TEXT,
    failure_strategy VARCHAR(30) NOT NULL DEFAULT 'STOP',
    retry_json TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(workflow_id, step_code),
    FOREIGN KEY(workflow_id) REFERENCES wt_workflow(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS wt_scope_variable (
    id VARCHAR(36) PRIMARY KEY,
    scope_type VARCHAR(30) NOT NULL,
    scope_id VARCHAR(36) NOT NULL,
    variable_key VARCHAR(200) NOT NULL,
    value_type VARCHAR(30) NOT NULL,
    value_json TEXT,
    sensitive INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(scope_type, scope_id, variable_key)
);

CREATE TABLE IF NOT EXISTS wt_hook_definition (
    id VARCHAR(36) PRIMARY KEY,
    owner_type VARCHAR(30) NOT NULL,
    owner_id VARCHAR(36) NOT NULL,
    hook_type VARCHAR(30) NOT NULL,
    enabled INTEGER NOT NULL DEFAULT 1,
    failure_strategy VARCHAR(30) NOT NULL DEFAULT 'STOP',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(owner_type, owner_id, hook_type)
);

CREATE TABLE IF NOT EXISTS wt_hook_step (
    id VARCHAR(36) PRIMARY KEY,
    hook_id VARCHAR(36) NOT NULL,
    step_code VARCHAR(100) NOT NULL,
    step_name VARCHAR(200) NOT NULL,
    step_type VARCHAR(30) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    config_json TEXT NOT NULL,
    extraction_json TEXT,
    assertion_json TEXT,
    failure_strategy VARCHAR(30) NOT NULL DEFAULT 'STOP',
    retry_json TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(hook_id, step_code),
    FOREIGN KEY(hook_id) REFERENCES wt_hook_definition(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS wt_runtime_datasource (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    driver_class VARCHAR(200) NOT NULL,
    jdbc_url TEXT NOT NULL,
    username VARCHAR(300),
    encrypted_password TEXT,
    options_json TEXT,
    allow_dangerous_sql INTEGER NOT NULL DEFAULT 0,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE(project_id, name),
    FOREIGN KEY(project_id) REFERENCES wt_project(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS wt_group_execution (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    group_id VARCHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_json TEXT,
    environment_snapshot TEXT NOT NULL,
    context_snapshot TEXT,
    started_at TEXT,
    finished_at TEXT,
    elapsed_ms INTEGER,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS wt_execution (
    id VARCHAR(36) PRIMARY KEY,
    group_execution_id VARCHAR(36),
    workflow_id VARCHAR(36) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_json TEXT,
    environment_snapshot TEXT NOT NULL,
    context_snapshot TEXT,
    workflow_snapshot TEXT NOT NULL,
    started_at TEXT,
    finished_at TEXT,
    elapsed_ms INTEGER,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS wt_hook_execution (
    id VARCHAR(36) PRIMARY KEY,
    group_execution_id VARCHAR(36),
    workflow_execution_id VARCHAR(36),
    hook_id VARCHAR(36) NOT NULL,
    hook_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    output_json TEXT,
    started_at TEXT,
    finished_at TEXT,
    elapsed_ms INTEGER,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS wt_step_execution (
    id VARCHAR(36) PRIMARY KEY,
    execution_id VARCHAR(36),
    hook_execution_id VARCHAR(36),
    step_id VARCHAR(36) NOT NULL,
    step_code VARCHAR(100) NOT NULL,
    phase VARCHAR(30) NOT NULL DEFAULT 'WORKFLOW',
    status VARCHAR(30) NOT NULL,
    request_json TEXT,
    response_json TEXT,
    output_json TEXT,
    extracted_json TEXT,
    assertion_json TEXT,
    started_at TEXT,
    finished_at TEXT,
    elapsed_ms INTEGER,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS idx_group_project ON wt_workflow_group(project_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_workflow_group ON wt_workflow(group_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_step_workflow ON wt_step_definition(workflow_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_variable_scope ON wt_scope_variable(scope_type, scope_id);
CREATE INDEX IF NOT EXISTS idx_execution_workflow ON wt_execution(workflow_id, started_at);
