package com.workflowtest.server.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workflowtest.security")
public record SecurityProperties(String jwtSecret, long tokenMinutes) {}
