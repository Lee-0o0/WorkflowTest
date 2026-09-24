package com.workflowtest.server.runtime;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.server.service.definition.DefinitionModels.EffectiveEnvironment;
import com.workflowtest.server.service.definition.DefinitionModels.ScopeType;
import com.workflowtest.server.persistence.entity.GlobalVariableEntity;
import com.workflowtest.server.persistence.entity.ScopeVariableEntity;
import com.workflowtest.server.persistence.mapper.GlobalVariableMapper;
import com.workflowtest.server.persistence.mapper.ScopeVariableMapper;
import com.workflowtest.server.support.EngineMessages;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EnvironmentResolver {
    /** application.yml 全局变量配置前缀 */
    public static final String GLOBAL_CONFIG_PREFIX = "workflowtest.global.";

    /** 环境变量来源标识 */
    public static final class Source {
        public static final String GLOBAL = "GLOBAL";
        public static final String PROJECT = "PROJECT";
        public static final String GROUP = "GROUP";
        public static final String WORKFLOW = "WORKFLOW";

        private Source() {}
    }

    private final ConfigurableEnvironment environment;
    private final GlobalVariableMapper globalVariableMapper;
    private final ScopeVariableMapper variableMapper;
    private final ObjectMapper objectMapper;

    public EnvironmentResolver(ConfigurableEnvironment environment,
                               GlobalVariableMapper globalVariableMapper,
                               ScopeVariableMapper variableMapper,
                               ObjectMapper objectMapper) {
        this.environment = environment;
        this.globalVariableMapper = globalVariableMapper;
        this.variableMapper = variableMapper;
        this.objectMapper = objectMapper;
    }

    public EffectiveEnvironment resolve(Long projectId, Long groupId, Long workflowId) {
        Map<String, Object> global = globalVariables();
        Map<String, Object> project = scoped(ScopeType.PROJECT, projectId);
        Map<String, Object> group = scoped(ScopeType.GROUP, groupId);
        Map<String, Object> workflow = scoped(ScopeType.WORKFLOW, workflowId);
        Map<String, Object> effective = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        merge(effective, sources, global, Source.GLOBAL);
        merge(effective, sources, project, Source.PROJECT);
        merge(effective, sources, group, Source.GROUP);
        merge(effective, sources, workflow, Source.WORKFLOW);
        return new EffectiveEnvironment(Map.copyOf(global), Map.copyOf(project), Map.copyOf(group),
                Map.copyOf(workflow), Map.copyOf(effective), Map.copyOf(sources));
    }

    private Map<String, Object> globalVariables() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (PropertySource<?> source : environment.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith(GLOBAL_CONFIG_PREFIX)) {
                        values.putIfAbsent(name.substring(GLOBAL_CONFIG_PREFIX.length()), environment.getProperty(name));
                    }
                }
            }
        }
        List<GlobalVariableEntity> entities = globalVariableMapper.selectList(
                Wrappers.<GlobalVariableEntity>lambdaQuery().eq(GlobalVariableEntity::getEnabled, true));
        for (GlobalVariableEntity entity : entities) {
            values.put(entity.getVariableKey(), readStoredValue(entity.getVariableKey(), entity.getValueJson()));
        }
        return values;
    }

    private Map<String, Object> scoped(ScopeType type, Long scopeId) {
        if (scopeId == null) return Map.of();
        List<ScopeVariableEntity> entities = variableMapper.selectList(
                Wrappers.<ScopeVariableEntity>lambdaQuery()
                        .eq(ScopeVariableEntity::getScopeType, type.name())
                        .eq(ScopeVariableEntity::getScopeId, scopeId)
                        .eq(ScopeVariableEntity::getEnabled, true));
        Map<String, Object> values = new LinkedHashMap<>();
        for (ScopeVariableEntity entity : entities) {
            values.put(entity.getVariableKey(), readStoredValue(entity.getVariableKey(), entity.getValueJson()));
        }
        return values;
    }

    private Object readStoredValue(String variableKey, String valueJson) {
        try {
            return objectMapper.readValue(valueJson, Object.class);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format(EngineMessages.ENV_VARIABLE_INVALID, variableKey), e);
        }
    }

    private void merge(Map<String, Object> values, Map<String, String> sources,
                       Map<String, Object> layer, String source) {
        layer.forEach((key, value) -> {
            values.put(key, value);
            sources.put(key, source);
        });
    }
}
