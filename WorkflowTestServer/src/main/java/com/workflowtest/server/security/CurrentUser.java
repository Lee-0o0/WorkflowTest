package com.workflowtest.server.security;

import com.workflowtest.server.domain.Roles.SystemRole;

public record CurrentUser(String id, String username, String displayName, SystemRole systemRole) {
    public boolean admin() { return systemRole == SystemRole.ADMIN; }
}
