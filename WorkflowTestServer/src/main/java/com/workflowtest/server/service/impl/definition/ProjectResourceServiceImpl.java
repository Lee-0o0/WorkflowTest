package com.workflowtest.server.service.impl.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.service.definition.DefinitionModels.ProjectResource;
import com.workflowtest.server.service.definition.DefinitionModels.ProjectResourceType;
import com.workflowtest.server.service.definition.ProjectResourceService;
import com.workflowtest.server.service.impl.support.DefinitionPersistenceSupport;
import com.workflowtest.server.persistence.entity.ProjectResourceEntity;
import com.workflowtest.server.persistence.mapper.ProjectResourceMapper;
import com.workflowtest.server.security.SecretCipher;
import com.workflowtest.server.support.JdbcConnectionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class ProjectResourceServiceImpl implements ProjectResourceService {
    private final ProjectResourceMapper projectResourceMapper;
    private final DefinitionPersistenceSupport support;
    private final SecretCipher secretCipher;

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResource> list(Long projectId) {
        return list(projectId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProjectResource> list(Long projectId, ProjectResourceType type) {
        var query = Wrappers.<ProjectResourceEntity>lambdaQuery()
                .eq(ProjectResourceEntity::getProjectId, projectId)
                .orderByAsc(ProjectResourceEntity::getResourceType, ProjectResourceEntity::getName);
        if (type != null) query.eq(ProjectResourceEntity::getResourceType, type.name());
        return projectResourceMapper.selectList(query).stream().map(support::toProjectResource).toList();
    }

    @Override
    public ProjectResource save(ProjectResource resource, String secret) {
        if (resource.name() == null || resource.name().isBlank()) throw new IllegalArgumentException("资源名称不能为空");
        ProjectResourceEntity entity = support.blank(resource.id()) ? new ProjectResourceEntity()
                : support.require(projectResourceMapper.selectById(resource.id()), "项目资源不存在");
        entity.setProjectId(resource.projectId());
        entity.setResourceType(resource.type().name());
        entity.setName(resource.name().trim());
        entity.setEnabled(resource.enabled());
        entity.setConfigJson(support.writeResourceConfig(resource, secret, entity));
        support.persist(projectResourceMapper, entity, resource.id());
        return support.toProjectResource(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean testConnection(Long id) {
        ProjectResourceEntity entity = support.require(projectResourceMapper.selectById(id), "项目资源不存在");
        if (!ProjectResourceType.DATASOURCE.name().equals(entity.getResourceType())) {
            throw new IllegalArgumentException("仅 JDBC 数据源支持连接测试");
        }
        Map<String, Object> config = support.readResourceConfig(entity.getConfigJson());
        testDatasourceConnection(config, support.string(config, "username"), null, id);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public void testDatasourceConnection(Map<String, Object> config, String username, String secret,
                                         Long existingResourceId) {
        if (config == null) throw new IllegalArgumentException("数据源配置不能为空");
        Map<String, Object> working = new LinkedHashMap<>(config);
        working.put("username", username == null ? "" : username);
        JdbcConnectionConfig.normalizeDatasourceConfig(working);
        String password = secret;
        if ((password == null || password.isEmpty()) && existingResourceId != null) {
            ProjectResourceEntity entity = projectResourceMapper.selectById(existingResourceId);
            if (entity != null) {
                Map<String, Object> stored = support.readResourceConfig(entity.getConfigJson());
                password = secretCipher.decrypt(support.string(stored, "encryptedPassword"));
            }
        }
        if (password == null) password = "";
        try {
            Class.forName(support.string(working, "driverClass"));
            try (var connection = DriverManager.getConnection(
                    JdbcConnectionConfig.resolveJdbcUrl(working), support.string(working, "username"), password)) {
                if (!connection.isValid(5)) throw new IllegalStateException("连接无效");
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("未找到 JDBC 驱动 " + support.string(working, "driverClass")
                    + "，请确认项目已引入对应驱动依赖", e);
        } catch (Exception e) {
            throw new IllegalStateException("数据源连接失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(Long id) {
        projectResourceMapper.deleteById(id);
    }

    @Override
    public void deleteByProject(Long projectId) {
        projectResourceMapper.delete(Wrappers.<ProjectResourceEntity>lambdaQuery()
                .eq(ProjectResourceEntity::getProjectId, projectId));
    }
}
