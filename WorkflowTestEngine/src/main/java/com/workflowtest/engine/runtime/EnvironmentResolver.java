package com.workflowtest.engine.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.api.DefinitionModels.EffectiveEnvironment;
import com.workflowtest.engine.api.DefinitionModels.ScopeType;
import com.workflowtest.engine.persistence.entity.ScopeVariableEntity;
import com.workflowtest.engine.persistence.mapper.ScopeVariableMapper;
import com.workflowtest.engine.security.SecretCipher;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

@Component
public class EnvironmentResolver {
    private static final String PREFIX = "workflowtest.global.";

    private final ConfigurableEnvironment environment;
    private final ScopeVariableMapper variableMapper;
    private final ObjectMapper objectMapper;
    private final SecretCipher secretCipher;

    public EnvironmentResolver(ConfigurableEnvironment environment,
                               ScopeVariableMapper variableMapper,
                               ObjectMapper objectMapper, SecretCipher secretCipher) {
        this.environment = environment;
        this.variableMapper = variableMapper;
        this.objectMapper = objectMapper;
        this.secretCipher = secretCipher;
    }

    public EffectiveEnvironment resolve(String projectId, String groupId, String workflowId) {
        Map<String, Object> global = globalVariables();
        Map<String, Object> project = scoped(ScopeType.PROJECT, projectId);
        Map<String, Object> group = scoped(ScopeType.GROUP, groupId);
        Map<String, Object> workflow = scoped(ScopeType.WORKFLOW, workflowId);
        Map<String, Object> effective = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        merge(effective, sources, global, "GLOBAL");
        merge(effective, sources, project, "PROJECT");
        merge(effective, sources, group, "GROUP");
        merge(effective, sources, workflow, "WORKFLOW");
        Set<String> sensitive = new LinkedHashSet<>();
        sensitive.addAll(sensitiveKeys(ScopeType.PROJECT, projectId));
        sensitive.addAll(sensitiveKeys(ScopeType.GROUP, groupId));
        sensitive.addAll(sensitiveKeys(ScopeType.WORKFLOW, workflowId));
        return new EffectiveEnvironment(Map.copyOf(global), Map.copyOf(project), Map.copyOf(group),
                Map.copyOf(workflow), Map.copyOf(effective), Map.copyOf(sources), Set.copyOf(sensitive));
    }

    private Map<String, Object> globalVariables() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith(PREFIX)) {
                        values.putIfAbsent(name.substring(PREFIX.length()), environment.getProperty(name));
                    }
                }
            }
        }
        return values;
    }

    private Map<String, Object> scoped(ScopeType type, String scopeId) {
        if (scopeId == null) return Map.of();
        List<ScopeVariableEntity> entities = variableMapper.selectList(
                Wrappers.<ScopeVariableEntity>lambdaQuery()
                        .eq(ScopeVariableEntity::getScopeType, type.name())
                        .eq(ScopeVariableEntity::getScopeId, scopeId)
                        .eq(ScopeVariableEntity::getEnabled, true));
        Map<String, Object> values = new LinkedHashMap<>();
        for (ScopeVariableEntity entity : entities) {
            values.put(entity.getVariableKey(), readValue(entity));
        }
        return values;
    }

    private Object readValue(ScopeVariableEntity entity) {
        try {
            String stored = entity.getValueJson();
            if (stored != null && stored.startsWith("ENC:")) stored = secretCipher.decrypt(stored.substring(4));
            return objectMapper.readValue(stored, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("环境变量格式无效: " + entity.getVariableKey(), e);
        }
    }

    private Set<String> sensitiveKeys(ScopeType type, String scopeId) {
        if (scopeId == null) return Set.of();
        Set<String> keys = new LinkedHashSet<>();
        variableMapper.selectList(Wrappers.<ScopeVariableEntity>lambdaQuery()
                        .eq(ScopeVariableEntity::getScopeType, type.name())
                        .eq(ScopeVariableEntity::getScopeId, scopeId)
                        .eq(ScopeVariableEntity::getEnabled, true)
                        .eq(ScopeVariableEntity::getSensitive, true))
                .forEach(entity -> keys.add(entity.getVariableKey()));
        return keys;
    }

    private void merge(Map<String, Object> values, Map<String, String> sources,
                       Map<String, Object> layer, String source) {
        layer.forEach((key, value) -> {
            values.put(key, value);
            sources.put(key, source);
        });
    }
}
