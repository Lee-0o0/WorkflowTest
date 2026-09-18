package com.workflowtest.server.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class SecuritySupport {
    private SecuritySupport() {}
    public static CurrentUser current() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CurrentUser user) return user;
        throw new IllegalStateException("用户未登录");
    }
}
