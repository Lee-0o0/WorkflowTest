-- 项目钩子：每个组执行前运行一次
CREATE TABLE IF NOT EXISTS ts_project_hook_definition (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id       INTEGER NOT NULL,
    hook_type        VARCHAR(30) NOT NULL,
    enabled          INTEGER NOT NULL DEFAULT 1,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    UNIQUE(project_id, hook_type)
);

CREATE TABLE IF NOT EXISTS ts_project_hook_step (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    project_hook_id  INTEGER NOT NULL,
    step_code        VARCHAR(100) NOT NULL,
    step_name        VARCHAR(200) NOT NULL,
    step_type        VARCHAR(30) NOT NULL,
    sort_order       INTEGER NOT NULL DEFAULT 0,
    enabled          INTEGER NOT NULL DEFAULT 1,
    config_json      TEXT NOT NULL,
    extraction_json  TEXT,
    assertion_json   TEXT,
    created_at       DATETIME NOT NULL,
    updated_at       DATETIME NOT NULL,
    UNIQUE(project_hook_id, step_code)
);

CREATE INDEX IF NOT EXISTS idx_project_hook ON ts_project_hook_definition(project_id, hook_type);
