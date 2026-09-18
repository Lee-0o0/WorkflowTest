package com.workflowtest.server.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflowtest.server.api.ApiModels.*;
import com.workflowtest.server.domain.Roles.ProjectRole;
import com.workflowtest.server.persistence.ScopeVariableMapper;
import com.workflowtest.server.persistence.entity.ScopeVariableEntity;
import com.workflowtest.server.security.SecuritySupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class VariableService {
    public enum Scope { PROJECT, GROUP, WORKFLOW }
    private static final ProjectRole[] READ = ProjectRole.values();
    private static final ProjectRole[] WRITE = {ProjectRole.PROJECT_ADMIN, ProjectRole.TEST_DEVELOPER};
    private final ScopeVariableMapper variables; private final ProjectAccessService access; private final ObjectMapper json;
    public VariableService(ScopeVariableMapper variables, ProjectAccessService access, ObjectMapper json) {
        this.variables = variables; this.access = access; this.json = json;
    }

    public List<VariableView> list(Scope scope, String scopeId) {
        access.require(projectId(scope, scopeId), READ);
        return entities(scope, scopeId).stream().map(this::view).toList();
    }

    @Transactional
    public VariableView save(Scope scope, String scopeId, String id, SaveVariableRequest request) {
        access.require(projectId(scope, scopeId), WRITE);
        ScopeVariableEntity entity = id == null ? new ScopeVariableEntity() : require(variables.selectById(id));
        if (id != null && (!scope.name().equals(entity.getScopeType()) || !scopeId.equals(entity.getScopeId())))
            throw new IllegalArgumentException("变量不属于指定范围");
        LocalDateTime now = LocalDateTime.now(); entity.setScopeType(scope.name()); entity.setScopeId(scopeId);
        entity.setVariableKey(request.key()); entity.setValueJson(write(request.value())); entity.setEnabled(request.enabled());
        entity.setUpdatedBy(SecuritySupport.current().id()); if (id == null) entity.setCreatedAt(now); entity.setUpdatedAt(now);
        if (id == null) variables.insert(entity); else variables.updateById(entity); return view(entity);
    }

    public void delete(Scope scope, String scopeId, String id) {
        access.require(projectId(scope, scopeId), WRITE);
        ScopeVariableEntity entity = require(variables.selectById(id));
        if (!scope.name().equals(entity.getScopeType()) || !scopeId.equals(entity.getScopeId()))
            throw new IllegalArgumentException("变量不属于指定范围");
        variables.deleteById(id);
    }

    public void deleteScope(Scope scope, String scopeId) {
        variables.delete(Wrappers.<ScopeVariableEntity>lambdaQuery().eq(ScopeVariableEntity::getScopeType, scope.name())
                .eq(ScopeVariableEntity::getScopeId, scopeId));
    }

    public ObjectNode values(Scope scope, String scopeId) {
        ObjectNode result = json.createObjectNode();
        entities(scope, scopeId).stream().filter(e -> Boolean.TRUE.equals(e.getEnabled()))
                .forEach(entity -> result.set(entity.getVariableKey(), read(entity.getValueJson())));
        return result;
    }

    private List<ScopeVariableEntity> entities(Scope scope, String id) {
        return variables.selectList(Wrappers.<ScopeVariableEntity>lambdaQuery().eq(ScopeVariableEntity::getScopeType, scope.name())
                .eq(ScopeVariableEntity::getScopeId, id).orderByAsc(ScopeVariableEntity::getVariableKey));
    }
    private String projectId(Scope scope, String id) { return switch (scope) {
        case PROJECT -> id; case GROUP -> access.projectOfGroup(id); case WORKFLOW -> access.projectOfWorkflow(id);
    }; }
    private VariableView view(ScopeVariableEntity e) { return new VariableView(e.getId(), e.getScopeType(), e.getScopeId(), e.getVariableKey(), read(e.getValueJson()), Boolean.TRUE.equals(e.getEnabled())); }
    private String write(JsonNode value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("变量值无效", e); } }
    private JsonNode read(String value) { try { return json.readTree(value); } catch (Exception e) { throw new IllegalStateException("变量值损坏", e); } }
    private ScopeVariableEntity require(ScopeVariableEntity value) { if (value == null) throw new IllegalArgumentException("变量不存在"); return value; }
}
