package com.workflowtest.server.domain;

public final class Roles {
    private Roles() {}
    public enum SystemRole { ADMIN, USER }
    public enum ProjectRole { PROJECT_ADMIN, TEST_DEVELOPER, TEST_EXECUTOR, VIEWER }
}
