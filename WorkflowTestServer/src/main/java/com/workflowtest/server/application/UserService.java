package com.workflowtest.server.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.domain.Roles.SystemRole;
import com.workflowtest.server.persistence.UserMapper;
import com.workflowtest.server.persistence.entity.UserEntity;
import com.workflowtest.server.security.*;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@EnableConfigurationProperties(BootstrapProperties.class)
public class UserService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final BootstrapProperties bootstrap;

    public UserService(UserMapper users, PasswordEncoder passwords, JwtService jwt, BootstrapProperties bootstrap) {
        this.users = users; this.passwords = passwords; this.jwt = jwt; this.bootstrap = bootstrap;
    }

    @PostConstruct
    @Transactional
    public void bootstrapAdmin() {
        if (users.selectCount(null) > 0) return;
        UserEntity entity = new UserEntity();
        entity.setUsername(bootstrap.adminUsername()); entity.setDisplayName(bootstrap.adminDisplayName());
        entity.setPasswordHash(passwords.encode(bootstrap.adminPassword())); entity.setSystemRole(SystemRole.ADMIN.name());
        entity.setEnabled(true); entity.setCreatedAt(LocalDateTime.now()); entity.setUpdatedAt(LocalDateTime.now());
        users.insert(entity);
    }

    public LoginResponse login(LoginRequest request) {
        UserEntity entity = users.selectOne(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getUsername, request.username()));
        if (entity == null || !Boolean.TRUE.equals(entity.getEnabled()) || !passwords.matches(request.password(), entity.getPasswordHash()))
            throw new IllegalArgumentException("用户名或密码错误");
        CurrentUser current = new CurrentUser(entity.getId(), entity.getUsername(), entity.getDisplayName(),
                SystemRole.valueOf(entity.getSystemRole()));
        return new LoginResponse(jwt.issue(current), view(entity));
    }

    public List<UserView> list() { requireAdmin(); return users.selectList(null).stream().map(this::view).toList(); }

    @Transactional
    public UserView save(String id, SaveUserRequest request) {
        requireAdmin();
        UserEntity entity = id == null ? new UserEntity() : require(users.selectById(id), "用户不存在");
        if (id == null && (request.password() == null || request.password().length() < 8))
            throw new IllegalArgumentException("新用户密码至少 8 位");
        entity.setUsername(request.username()); entity.setDisplayName(request.displayName());
        entity.setSystemRole(request.systemRole().name()); entity.setEnabled(request.enabled());
        if (request.password() != null && !request.password().isBlank()) entity.setPasswordHash(passwords.encode(request.password()));
        LocalDateTime now = LocalDateTime.now(); if (id == null) entity.setCreatedAt(now); entity.setUpdatedAt(now);
        if (id == null) users.insert(entity); else users.updateById(entity);
        return view(entity);
    }

    public UserEntity byId(String id) { return require(users.selectById(id), "用户不存在"); }
    private UserView view(UserEntity e) { return new UserView(e.getId(), e.getUsername(), e.getDisplayName(), SystemRole.valueOf(e.getSystemRole()), Boolean.TRUE.equals(e.getEnabled())); }
    private void requireAdmin() { if (!SecuritySupport.current().admin()) throw new org.springframework.security.access.AccessDeniedException("需要系统管理员权限"); }
    private <T> T require(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
}
