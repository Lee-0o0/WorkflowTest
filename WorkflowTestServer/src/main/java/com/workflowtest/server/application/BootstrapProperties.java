package com.workflowtest.server.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("workflowtest.bootstrap")
public record BootstrapProperties(String adminUsername, String adminPassword, String adminDisplayName) {}
