CREATE TABLE wt_server_variable (
    id VARCHAR(36) PRIMARY KEY,
    scope_type VARCHAR(30) NOT NULL,
    scope_id VARCHAR(36) NOT NULL,
    variable_key VARCHAR(200) NOT NULL,
    value_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_server_variable UNIQUE(scope_type, scope_id, variable_key)
);

CREATE INDEX idx_server_variable_scope ON wt_server_variable(scope_type, scope_id);
