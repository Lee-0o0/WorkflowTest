package com.workflowtest.engine.application.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.persistence.entity.*;
import com.workflowtest.engine.security.SecretCipher;
import com.workflowtest.engine.support.JdbcConnectionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class DefinitionPersistenceSupport {
    private final ObjectMapper objectMapper;
    private final SecretCipher secretCipher;

    public void requireName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("名称不能为空");
    }

    public boolean blank(Long value) {
        return value == null;
    }

    public <T> T require(T value, String message) {
        if (value == null) throw new IllegalArgumentException(message);
        return value;
    }

    public <T> void persist(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper, T entity, Long id) {
        if (blank(id)) mapper.insert(entity);
        else mapper.updateById(entity);
    }

    public <T> void reorder(List<T> items, Long id, int delta,
                            java.util.function.Function<T, Long> idReader,
                            java.util.function.BiConsumer<T, Integer> writer) {
        int from = -1;
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(idReader.apply(items.get(i)), id)) {
                from = i;
                break;
            }
        }
        if (from < 0) throw new IllegalArgumentException("排序对象不存在");
        int target = Math.max(0, Math.min(items.size() - 1, from + Integer.signum(delta)));
        if (target != from) {
            T item = items.remove(from);
            items.add(target, item);
        }
        for (int i = 0; i < items.size(); i++) writer.accept(items.get(i), i);
    }

    public void validateStep(Step step) {
        if (step == null || step.code() == null || !step.code().matches("[A-Za-z][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("步骤编码必须以字母开头，且只能包含字母、数字、_、-");
        }
        if (step.name() == null || step.name().isBlank()) throw new IllegalArgumentException("步骤名称不能为空");
        try {
            objectMapper.readTree(step.configJson());
        } catch (Exception e) {
            throw new IllegalArgumentException("步骤配置不是有效 JSON", e);
        }
    }

    public void copyStep(Step source, StepEntity target) {
        target.setStepCode(source.code());
        target.setStepName(source.name());
        target.setStepType(source.type().name());
        target.setSortOrder(source.sortOrder());
        target.setEnabled(source.enabled());
        target.setConfigJson(source.configJson());
        target.setExtractionJson(emptyArray(source.extractionJson()));
        target.setAssertionJson(emptyArray(source.assertionJson()));
    }

    public void copyStep(Step source, HookStepEntity target) {
        target.setStepCode(source.code());
        target.setStepName(source.name());
        target.setStepType(source.type().name());
        target.setSortOrder(source.sortOrder());
        target.setEnabled(source.enabled());
        target.setConfigJson(source.configJson());
        target.setExtractionJson(emptyArray(source.extractionJson()));
        target.setAssertionJson(emptyArray(source.assertionJson()));
    }

    public Project toProject(ProjectEntity entity) {
        return new Project(entity.getId(), entity.getName(), entity.getDescription(), Boolean.TRUE.equals(entity.getEnabled()));
    }

    public Group toGroup(WorkflowGroupEntity entity) {
        return new Group(entity.getId(), entity.getProjectId(), entity.getName(), entity.getDescription(),
                entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()));
    }

    public Workflow toWorkflow(WorkflowEntity entity) {
        return new Workflow(entity.getId(), entity.getGroupId(), entity.getName(), entity.getDescription(),
                entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()));
    }

    public Step toStep(StepEntity entity) {
        return new Step(entity.getId(), entity.getWorkflowId(), entity.getStepCode(), entity.getStepName(),
                StepType.valueOf(entity.getStepType()), entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()),
                entity.getConfigJson(), entity.getExtractionJson(), entity.getAssertionJson(), false);
    }

    public Step toHookStep(HookStepEntity entity) {
        return new Step(entity.getId(), entity.getHookId(), entity.getStepCode(), entity.getStepName(),
                StepType.valueOf(entity.getStepType()), entity.getSortOrder(), Boolean.TRUE.equals(entity.getEnabled()),
                entity.getConfigJson(), entity.getExtractionJson(), entity.getAssertionJson(), true);
    }

    public Hook toHook(HookEntity entity, List<Step> steps) {
        return new Hook(entity.getId(), entity.getGroupId(), HookType.valueOf(entity.getHookType()),
                Boolean.TRUE.equals(entity.getEnabled()), steps);
    }

    public ScopedVariable toVariable(ScopeVariableEntity entity) {
        return new ScopedVariable(entity.getId(), ScopeType.valueOf(entity.getScopeType()), entity.getScopeId(),
                entity.getVariableKey(), entity.getValueType(),
                readStoredValue(entity.getVariableKey(), entity.getValueJson()), Boolean.TRUE.equals(entity.getEnabled()));
    }

    public GlobalVariable toGlobalVariable(GlobalVariableEntity entity) {
        return new GlobalVariable(entity.getId(), entity.getVariableKey(), entity.getValueType(),
                readStoredValue(entity.getVariableKey(), entity.getValueJson()), Boolean.TRUE.equals(entity.getEnabled()));
    }

    public ProjectResource toProjectResource(ProjectResourceEntity entity) {
        Map<String, Object> config = new LinkedHashMap<>(readResourceConfig(entity.getConfigJson()));
        if (config.containsKey("encryptedPassword")) config.put("encryptedPassword", "******");
        return new ProjectResource(entity.getId(), entity.getProjectId(),
                ProjectResourceType.valueOf(entity.getResourceType()), entity.getName(),
                Map.copyOf(config), Boolean.TRUE.equals(entity.getEnabled()));
    }

    public String writeResourceConfig(ProjectResource resource, String secret, ProjectResourceEntity existing) {
        Map<String, Object> config = new LinkedHashMap<>(resource.config() == null ? Map.of() : resource.config());
        if (resource.type() == ProjectResourceType.DATASOURCE) {
            JdbcConnectionConfig.normalizeDatasourceConfig(config);
            if (secret != null && !secret.isEmpty()) {
                config.put("encryptedPassword", secretCipher.encrypt(secret));
            } else if (existing.getConfigJson() != null && !existing.getConfigJson().isBlank()) {
                Map<String, Object> previous = readResourceConfig(existing.getConfigJson());
                if (previous.containsKey("encryptedPassword")) config.put("encryptedPassword", previous.get("encryptedPassword"));
            } else {
                config.put("encryptedPassword", secretCipher.encrypt(""));
            }
        } else if (resource.type() == ProjectResourceType.FILE) {
            if (string(config, "path").isBlank()) throw new IllegalArgumentException("文件路径不能为空");
            if (!config.containsKey("encoding") || string(config, "encoding").isBlank()) config.put("encoding", "UTF-8");
        }
        try {
            return objectMapper.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("资源配置无法序列化", e);
        }
    }

    public Map<String, Object> readResourceConfig(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("资源配置 JSON 无效", e);
        }
    }

    public String serializeVariableValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("变量值无法序列化", e);
        }
    }

    public Object readStoredValue(String key, String stored) {
        try {
            return objectMapper.readValue(stored, Object.class);
        } catch (Exception ex) {
            return null;
        }
    }

    public String string(Map<String, Object> config, String key) {
        Object value = config.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String emptyArray(String value) {
        return value == null || value.isBlank() ? "[]" : value;
    }
}
