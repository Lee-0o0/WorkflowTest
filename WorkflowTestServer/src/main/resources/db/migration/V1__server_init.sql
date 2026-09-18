CREATE TABLE wt_user (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    system_role VARCHAR(30) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE wt_server_project (
    id VARCHAR(36) PRIMARY KEY,
    project_key VARCHAR(60) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE wt_project_member (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    project_role VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_project_member UNIQUE(project_id, user_id),
    CONSTRAINT fk_member_project FOREIGN KEY(project_id) REFERENCES wt_server_project(id) ON DELETE CASCADE,
    CONSTRAINT fk_member_user FOREIGN KEY(user_id) REFERENCES wt_user(id) ON DELETE CASCADE
);

CREATE TABLE wt_server_group (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_server_group UNIQUE(project_id, name),
    CONSTRAINT fk_group_project FOREIGN KEY(project_id) REFERENCES wt_server_project(id) ON DELETE CASCADE
);

CREATE TABLE wt_server_workflow (
    id VARCHAR(36) PRIMARY KEY,
    group_id VARCHAR(36) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    revision INTEGER NOT NULL DEFAULT 0,
    draft_json TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_server_workflow UNIQUE(group_id, name),
    CONSTRAINT fk_workflow_group FOREIGN KEY(group_id) REFERENCES wt_server_group(id) ON DELETE CASCADE
);

CREATE TABLE wt_workflow_version (
    id VARCHAR(36) PRIMARY KEY,
    workflow_id VARCHAR(36) NOT NULL,
    version_no INTEGER NOT NULL,
    revision INTEGER NOT NULL,
    snapshot_json TEXT NOT NULL,
    checksum VARCHAR(100) NOT NULL,
    published_by VARCHAR(36) NOT NULL,
    published_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_workflow_version UNIQUE(workflow_id, version_no),
    CONSTRAINT fk_version_workflow FOREIGN KEY(workflow_id) REFERENCES wt_server_workflow(id) ON DELETE CASCADE
);

CREATE INDEX idx_member_user ON wt_project_member(user_id, project_id);
CREATE INDEX idx_group_project_server ON wt_server_group(project_id, sort_order);
CREATE INDEX idx_workflow_group_server ON wt_server_workflow(group_id, sort_order);
CREATE INDEX idx_version_workflow ON wt_workflow_version(workflow_id, version_no);
