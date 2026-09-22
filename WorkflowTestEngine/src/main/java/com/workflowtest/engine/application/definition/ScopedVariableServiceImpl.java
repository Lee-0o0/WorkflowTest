package com.workflowtest.engine.application.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.definition.DefinitionModels.ScopeType;
import com.workflowtest.engine.api.definition.DefinitionModels.ScopedVariable;
import com.workflowtest.engine.api.definition.ScopedVariableService;
import com.workflowtest.engine.application.support.DefinitionPersistenceSupport;
import com.workflowtest.engine.persistence.entity.ScopeVariableEntity;
import com.workflowtest.engine.persistence.mapper.ScopeVariableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class ScopedVariableServiceImpl implements ScopedVariableService {
    private final ScopeVariableMapper variableMapper;
    private final DefinitionPersistenceSupport support;

    @Override
    @Transactional(readOnly = true)
    public List<ScopedVariable> list(ScopeType type, Long scopeId) {
        return variableMapper.selectList(Wrappers.<ScopeVariableEntity>lambdaQuery()
                        .eq(ScopeVariableEntity::getScopeType, type.name())
                        .eq(ScopeVariableEntity::getScopeId, scopeId)
                        .orderByAsc(ScopeVariableEntity::getVariableKey))
                .stream().map(support::toVariable).toList();
    }

    @Override
    public ScopedVariable save(ScopedVariable variable) {
        if (variable.key() == null || variable.key().isBlank()) throw new IllegalArgumentException("变量名不能为空");
        ScopeVariableEntity entity = support.blank(variable.id()) ? new ScopeVariableEntity()
                : support.require(variableMapper.selectById(variable.id()), "变量不存在");
        entity.setScopeType(variable.scopeType().name());
        entity.setScopeId(variable.scopeId());
        entity.setVariableKey(variable.key().trim());
        entity.setValueType(variable.valueType() == null ? "AUTO" : variable.valueType());
        entity.setValueJson(support.serializeVariableValue(variable.value()));
        entity.setEnabled(variable.enabled());
        support.persist(variableMapper, entity, variable.id());
        return support.toVariable(entity);
    }

    @Override
    public void delete(Long id) {
        variableMapper.deleteById(id);
    }

    @Override
    public void deleteByScope(ScopeType type, Long scopeId) {
        variableMapper.delete(Wrappers.<ScopeVariableEntity>lambdaQuery()
                .eq(ScopeVariableEntity::getScopeType, type.name())
                .eq(ScopeVariableEntity::getScopeId, scopeId));
    }
}
