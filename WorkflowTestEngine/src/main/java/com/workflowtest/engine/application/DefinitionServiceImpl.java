package com.workflowtest.engine.application;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.DefinitionModels.*;
import com.workflowtest.engine.api.DefinitionService;
import com.workflowtest.engine.persistence.entity.*;
import com.workflowtest.engine.persistence.mapper.*;
import com.workflowtest.engine.runtime.EnvironmentResolver;
import com.workflowtest.engine.security.SecretCipher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class DefinitionServiceImpl implements DefinitionService {
    private final ProjectMapper projectMapper;
    private final WorkflowGroupMapper groupMapper;
    private final WorkflowMapper workflowMapper;
    private final StepMapper stepMapper;
    private final ScopeVariableMapper variableMapper;
    private final HookMapper hookMapper;
    private final HookStepMapper hookStepMapper;
    private final RuntimeDataSourceMapper dataSourceMapper;
    private final EnvironmentResolver environmentResolver;
    private final ObjectMapper objectMapper;
    private final SecretCipher secretCipher;

    public DefinitionServiceImpl(ProjectMapper projectMapper, WorkflowGroupMapper groupMapper,
                                 WorkflowMapper workflowMapper, StepMapper stepMapper,
                                 ScopeVariableMapper variableMapper, HookMapper hookMapper,
                                 HookStepMapper hookStepMapper, RuntimeDataSourceMapper dataSourceMapper,
                                 EnvironmentResolver environmentResolver, ObjectMapper objectMapper,
                                 SecretCipher secretCipher) {
        this.projectMapper = projectMapper;
        this.groupMapper = groupMapper;
        this.workflowMapper = workflowMapper;
        this.stepMapper = stepMapper;
        this.variableMapper = variableMapper;
        this.hookMapper = hookMapper;
        this.hookStepMapper = hookStepMapper;
        this.dataSourceMapper = dataSourceMapper;
        this.environmentResolver = environmentResolver;
        this.objectMapper = objectMapper;
        this.secretCipher = secretCipher;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectTree loadTree() {
        List<ProjectNode> projects = projectMapper.selectList(
                        Wrappers.<ProjectEntity>lambdaQuery().orderByAsc(ProjectEntity::getName))
                .stream().map(project -> new ProjectNode(toProject(project), groups(project.getId()))).toList();
        return new ProjectTree(projects);
    }

    private List<GroupNode> groups(String projectId) {
        return groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                        .eq(WorkflowGroupEntity::getProjectId, projectId)
                        .orderByAsc(WorkflowGroupEntity::getSortOrder, WorkflowGroupEntity::getName))
                .stream().map(group -> new GroupNode(toGroup(group), workflows(group.getId()))).toList();
    }

    private List<WorkflowNode> workflows(String groupId) {
        return workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                        .eq(WorkflowEntity::getGroupId, groupId)
                        .orderByAsc(WorkflowEntity::getSortOrder, WorkflowEntity::getName))
                .stream().map(workflow -> new WorkflowNode(toWorkflow(workflow), workflowSteps(workflow.getId())))
                .toList();
    }

    private List<Step> workflowSteps(String workflowId) {
        return stepMapper.selectList(Wrappers.<StepEntity>lambdaQuery()
                        .eq(StepEntity::getWorkflowId, workflowId)
                        .orderByAsc(StepEntity::getSortOrder))
                .stream().map(this::toStep).toList();
    }

    @Override
    public Project saveProject(String id, String name, String description) {
        requireName(name);
        ProjectEntity entity = blank(id) ? new ProjectEntity() : require(projectMapper.selectById(id), "项目不存在");
        entity.setName(name.trim());
        entity.setDescription(description);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        persist(projectMapper, entity, id);
        return toProject(entity);
    }

    @Override
    public Group saveGroup(String id, String projectId, String name, String description, int sortOrder) {
        require(projectMapper.selectById(projectId), "项目不存在");
        requireName(name);
        WorkflowGroupEntity entity = blank(id) ? new WorkflowGroupEntity() : require(groupMapper.selectById(id), "组不存在");
        entity.setProjectId(projectId);
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setSortOrder(sortOrder);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        persist(groupMapper, entity, id);
        return toGroup(entity);
    }

    @Override
    public Workflow saveWorkflow(String id, String groupId, String name, String description, int sortOrder) {
        require(groupMapper.selectById(groupId), "组不存在");
        requireName(name);
        WorkflowEntity entity = blank(id) ? new WorkflowEntity() : require(workflowMapper.selectById(id), "工作流不存在");
        entity.setGroupId(groupId);
        entity.setName(name.trim());
        entity.setDescription(description);
        entity.setSortOrder(sortOrder);
        if (entity.getEnabled() == null) entity.setEnabled(true);
        persist(workflowMapper, entity, id);
        return toWorkflow(entity);
    }

    @Override
    public Step saveWorkflowStep(Step step) {
        validateStep(step);
        require(workflowMapper.selectById(step.ownerId()), "工作流不存在");
        StepEntity entity = blank(step.id()) ? new StepEntity() : require(stepMapper.selectById(step.id()), "步骤不存在");
        copyStep(step, entity);
        entity.setWorkflowId(step.ownerId());
        persist(stepMapper, entity, step.id());
        return toStep(entity);
    }

    @Override
    public void moveGroup(String id, int delta) {
        WorkflowGroupEntity current = require(groupMapper.selectById(id), "组不存在");
        List<WorkflowGroupEntity> items = groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                .eq(WorkflowGroupEntity::getProjectId, current.getProjectId())
                .orderByAsc(WorkflowGroupEntity::getSortOrder, WorkflowGroupEntity::getName));
        reorder(items, id, delta, WorkflowGroupEntity::getId, (item, order) -> { item.setSortOrder(order); groupMapper.updateById(item); });
    }

    @Override
    public void moveWorkflow(String id, int delta) {
        WorkflowEntity current = require(workflowMapper.selectById(id), "工作流不存在");
        List<WorkflowEntity> items = workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                .eq(WorkflowEntity::getGroupId, current.getGroupId())
                .orderByAsc(WorkflowEntity::getSortOrder, WorkflowEntity::getName));
        reorder(items, id, delta, WorkflowEntity::getId, (item, order) -> { item.setSortOrder(order); workflowMapper.updateById(item); });
    }

    @Override
    public void moveStep(String id, boolean hookStep, int delta) {
        if (hookStep) {
            HookStepEntity current = require(hookStepMapper.selectById(id), "钩子步骤不存在");
            List<HookStepEntity> items = hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                    .eq(HookStepEntity::getHookId, current.getHookId()).orderByAsc(HookStepEntity::getSortOrder));
            reorder(items, id, delta, HookStepEntity::getId, (item, order) -> { item.setSortOrder(order); hookStepMapper.updateById(item); });
        } else {
            StepEntity current = require(stepMapper.selectById(id), "步骤不存在");
            List<StepEntity> items = stepMapper.selectList(Wrappers.<StepEntity>lambdaQuery()
                    .eq(StepEntity::getWorkflowId, current.getWorkflowId()).orderByAsc(StepEntity::getSortOrder));
            reorder(items, id, delta, StepEntity::getId, (item, order) -> { item.setSortOrder(order); stepMapper.updateById(item); });
        }
    }

    @Override
    public void deleteProject(String id) {
        for (GroupNode group : groups(id)) deleteGroup(group.group().id());
        deleteScope(ScopeType.PROJECT, id);
        dataSourceMapper.delete(Wrappers.<RuntimeDataSourceEntity>lambdaQuery()
                .eq(RuntimeDataSourceEntity::getProjectId, id));
        projectMapper.deleteById(id);
    }

    @Override
    public void deleteGroup(String id) {
        for (WorkflowNode workflow : workflows(id)) deleteWorkflow(workflow.workflow().id());
        deleteHook(OwnerType.GROUP, id);
        deleteScope(ScopeType.GROUP, id);
        groupMapper.deleteById(id);
    }

    @Override
    public void deleteWorkflow(String id) {
        stepMapper.delete(Wrappers.<StepEntity>lambdaQuery().eq(StepEntity::getWorkflowId, id));
        deleteHook(OwnerType.WORKFLOW, id);
        deleteScope(ScopeType.WORKFLOW, id);
        workflowMapper.deleteById(id);
    }

    @Override
    public void deleteStep(String id, boolean hookStep) {
        if (hookStep) hookStepMapper.deleteById(id); else stepMapper.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScopedVariable> listVariables(ScopeType type, String scopeId) {
        return variableMapper.selectList(Wrappers.<ScopeVariableEntity>lambdaQuery()
                        .eq(ScopeVariableEntity::getScopeType, type.name())
                        .eq(ScopeVariableEntity::getScopeId, scopeId)
                        .orderByAsc(ScopeVariableEntity::getVariableKey))
                .stream().map(this::toVariable).toList();
    }

    @Override
    public ScopedVariable saveVariable(ScopedVariable variable) {
        if (variable.key() == null || variable.key().isBlank()) throw new IllegalArgumentException("变量名不能为空");
        ScopeVariableEntity entity = blank(variable.id()) ? new ScopeVariableEntity()
                : require(variableMapper.selectById(variable.id()), "变量不存在");
        entity.setScopeType(variable.scopeType().name());
        entity.setScopeId(variable.scopeId());
        entity.setVariableKey(variable.key().trim());
        entity.setValueType(variable.valueType() == null ? "AUTO" : variable.valueType());
        try {
            String json = objectMapper.writeValueAsString(variable.value());
            entity.setValueJson(variable.sensitive() ? "ENC:" + secretCipher.encrypt(json) : json);
        } catch (Exception e) {
            throw new IllegalArgumentException("变量值无法序列化", e);
        }
        entity.setSensitive(variable.sensitive());
        entity.setEnabled(variable.enabled());
        persist(variableMapper, entity, variable.id());
        return toVariable(entity);
    }

    @Override public void deleteVariable(String id) { variableMapper.deleteById(id); }

    @Override
    @Transactional(readOnly = true)
    public EffectiveEnvironment previewEnvironment(String projectId, String groupId, String workflowId) {
        return environmentResolver.resolve(projectId, groupId, workflowId);
    }

    @Override
    public Hook getOrCreateHook(OwnerType ownerType, String ownerId, HookType hookType) {
        HookEntity entity = hookMapper.selectOne(Wrappers.<HookEntity>lambdaQuery()
                .eq(HookEntity::getOwnerType, ownerType.name())
                .eq(HookEntity::getOwnerId, ownerId)
                .eq(HookEntity::getHookType, hookType.name()));
        if (entity == null) {
            entity = new HookEntity();
            entity.setOwnerType(ownerType.name());
            entity.setOwnerId(ownerId);
            entity.setHookType(hookType.name());
            entity.setEnabled(true);
            entity.setFailureStrategy(FailureStrategy.STOP.name());
            hookMapper.insert(entity);
        }
        return toHook(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Hook> listHooks(OwnerType ownerType, String ownerId) {
        return hookMapper.selectList(Wrappers.<HookEntity>lambdaQuery()
                        .eq(HookEntity::getOwnerType, ownerType.name())
                        .eq(HookEntity::getOwnerId, ownerId)
                        .orderByAsc(HookEntity::getHookType))
                .stream().map(this::toHook).toList();
    }

    @Override
    public Step saveHookStep(String hookId, Step step) {
        validateStep(step);
        require(hookMapper.selectById(hookId), "钩子不存在");
        HookStepEntity entity = blank(step.id()) ? new HookStepEntity()
                : require(hookStepMapper.selectById(step.id()), "钩子步骤不存在");
        copyStep(step, entity);
        entity.setHookId(hookId);
        persist(hookStepMapper, entity, step.id());
        return toStep(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuntimeDataSource> listDataSources(String projectId) {
        return dataSourceMapper.selectList(Wrappers.<RuntimeDataSourceEntity>lambdaQuery()
                        .eq(RuntimeDataSourceEntity::getProjectId, projectId)
                        .orderByAsc(RuntimeDataSourceEntity::getName))
                .stream().map(this::toDataSource).toList();
    }

    @Override
    public RuntimeDataSource saveDataSource(RuntimeDataSource source, String password) {
        RuntimeDataSourceEntity entity = blank(source.id()) ? new RuntimeDataSourceEntity()
                : require(dataSourceMapper.selectById(source.id()), "数据源不存在");
        entity.setProjectId(source.projectId());
        entity.setName(source.name());
        entity.setDriverClass(source.driverClass());
        entity.setJdbcUrl(source.jdbcUrl());
        entity.setUsername(source.username());
        if (password != null && !password.isEmpty()) entity.setEncryptedPassword(secretCipher.encrypt(password));
        entity.setAllowDangerousSql(source.allowDangerousSql());
        entity.setEnabled(source.enabled());
        entity.setOptionsJson("{}");
        persist(dataSourceMapper, entity, source.id());
        return toDataSource(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean testDataSource(String id) {
        RuntimeDataSourceEntity source = require(dataSourceMapper.selectById(id), "数据源不存在");
        try {
            Class.forName(source.getDriverClass());
            try (var connection = DriverManager.getConnection(source.getJdbcUrl(), source.getUsername(),
                    secretCipher.decrypt(source.getEncryptedPassword()))) {
                return connection.isValid(5);
            }
        } catch (Exception e) {
            throw new IllegalStateException("数据源连接失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteDataSource(String id) {
        dataSourceMapper.deleteById(id);
    }

    private Hook toHook(HookEntity entity) {
        List<Step> steps = hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                        .eq(HookStepEntity::getHookId, entity.getId())
                        .orderByAsc(HookStepEntity::getSortOrder))
                .stream().map(this::toStep).toList();
        return new Hook(entity.getId(), OwnerType.valueOf(entity.getOwnerType()), entity.getOwnerId(),
                HookType.valueOf(entity.getHookType()), Boolean.TRUE.equals(entity.getEnabled()),
                FailureStrategy.valueOf(entity.getFailureStrategy()), steps);
    }

    private void deleteHook(OwnerType type, String ownerId) {
        List<HookEntity> hooks = hookMapper.selectList(Wrappers.<HookEntity>lambdaQuery()
                .eq(HookEntity::getOwnerType, type.name()).eq(HookEntity::getOwnerId, ownerId));
        for (HookEntity hook : hooks) {
            hookStepMapper.delete(Wrappers.<HookStepEntity>lambdaQuery().eq(HookStepEntity::getHookId, hook.getId()));
            hookMapper.deleteById(hook.getId());
        }
    }

    private void deleteScope(ScopeType type, String id) {
        variableMapper.delete(Wrappers.<ScopeVariableEntity>lambdaQuery()
                .eq(ScopeVariableEntity::getScopeType, type.name()).eq(ScopeVariableEntity::getScopeId, id));
    }

    private void validateStep(Step step) {
        if (step == null || step.code() == null || !step.code().matches("[A-Za-z][A-Za-z0-9_-]*"))
            throw new IllegalArgumentException("步骤编码必须以字母开头，且只能包含字母、数字、_、-");
        if (step.name() == null || step.name().isBlank()) throw new IllegalArgumentException("步骤名称不能为空");
        try { objectMapper.readTree(step.configJson()); } catch (Exception e) { throw new IllegalArgumentException("步骤配置不是有效 JSON", e); }
    }

    private void copyStep(Step source, StepEntity target) {
        target.setStepCode(source.code()); target.setStepName(source.name()); target.setStepType(source.type().name());
        target.setSortOrder(source.sortOrder()); target.setEnabled(source.enabled()); target.setConfigJson(source.configJson());
        target.setExtractionJson(emptyArray(source.extractionJson())); target.setAssertionJson(emptyArray(source.assertionJson()));
        target.setFailureStrategy(source.failureStrategy().name()); target.setRetryJson(source.retryJson());
    }

    private void copyStep(Step source, HookStepEntity target) {
        target.setStepCode(source.code()); target.setStepName(source.name()); target.setStepType(source.type().name());
        target.setSortOrder(source.sortOrder()); target.setEnabled(source.enabled()); target.setConfigJson(source.configJson());
        target.setExtractionJson(emptyArray(source.extractionJson())); target.setAssertionJson(emptyArray(source.assertionJson()));
        target.setFailureStrategy(source.failureStrategy().name()); target.setRetryJson(source.retryJson());
    }

    private String emptyArray(String value) { return value == null || value.isBlank() ? "[]" : value; }
    private void requireName(String name) { if (name == null || name.isBlank()) throw new IllegalArgumentException("名称不能为空"); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private <T> T require(T value, String message) { if (value == null) throw new IllegalArgumentException(message); return value; }
    private <T> void persist(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, T entity, String id) {
        if (blank(id)) mapper.insert(entity); else mapper.updateById(entity);
    }

    private <T> void reorder(List<T> items, String id, int delta,
                             java.util.function.Function<T, String> idReader,
                             java.util.function.BiConsumer<T, Integer> writer) {
        int from = -1;
        for (int i = 0; i < items.size(); i++) if (Objects.equals(idReader.apply(items.get(i)), id)) { from = i; break; }
        if (from < 0) throw new IllegalArgumentException("排序对象不存在");
        int target = Math.max(0, Math.min(items.size() - 1, from + Integer.signum(delta)));
        if (target != from) {
            T item = items.remove(from); items.add(target, item);
        }
        for (int i = 0; i < items.size(); i++) writer.accept(items.get(i), i);
    }

    private Project toProject(ProjectEntity e) { return new Project(e.getId(), e.getName(), e.getDescription(), Boolean.TRUE.equals(e.getEnabled())); }
    private Group toGroup(WorkflowGroupEntity e) { return new Group(e.getId(), e.getProjectId(), e.getName(), e.getDescription(), e.getSortOrder(), Boolean.TRUE.equals(e.getEnabled())); }
    private Workflow toWorkflow(WorkflowEntity e) { return new Workflow(e.getId(), e.getGroupId(), e.getName(), e.getDescription(), e.getSortOrder(), Boolean.TRUE.equals(e.getEnabled())); }
    private Step toStep(StepEntity e) { return new Step(e.getId(), e.getWorkflowId(), e.getStepCode(), e.getStepName(), StepType.valueOf(e.getStepType()), e.getSortOrder(), Boolean.TRUE.equals(e.getEnabled()), e.getConfigJson(), e.getExtractionJson(), e.getAssertionJson(), FailureStrategy.valueOf(e.getFailureStrategy()), e.getRetryJson(), false); }
    private Step toStep(HookStepEntity e) { return new Step(e.getId(), e.getHookId(), e.getStepCode(), e.getStepName(), StepType.valueOf(e.getStepType()), e.getSortOrder(), Boolean.TRUE.equals(e.getEnabled()), e.getConfigJson(), e.getExtractionJson(), e.getAssertionJson(), FailureStrategy.valueOf(e.getFailureStrategy()), e.getRetryJson(), true); }
    private RuntimeDataSource toDataSource(RuntimeDataSourceEntity e) { return new RuntimeDataSource(e.getId(), e.getProjectId(), e.getName(), e.getDriverClass(), e.getJdbcUrl(), e.getUsername(), Boolean.TRUE.equals(e.getAllowDangerousSql()), Boolean.TRUE.equals(e.getEnabled())); }
    private ScopedVariable toVariable(ScopeVariableEntity e) {
        Object value;
        try {
            String stored = e.getValueJson();
            if (stored != null && stored.startsWith("ENC:")) stored = secretCipher.decrypt(stored.substring(4));
            value = objectMapper.readValue(stored, Object.class);
        } catch (Exception ex) { value = null; }
        return new ScopedVariable(e.getId(), ScopeType.valueOf(e.getScopeType()), e.getScopeId(), e.getVariableKey(), e.getValueType(), value, Boolean.TRUE.equals(e.getSensitive()), Boolean.TRUE.equals(e.getEnabled()));
    }
}
