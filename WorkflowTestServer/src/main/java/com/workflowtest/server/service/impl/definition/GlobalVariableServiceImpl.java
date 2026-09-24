package com.workflowtest.server.service.impl.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.server.service.definition.DefinitionModels.GlobalVariable;
import com.workflowtest.server.service.definition.GlobalVariableService;
import com.workflowtest.server.service.impl.support.DefinitionPersistenceSupport;
import com.workflowtest.server.persistence.entity.GlobalVariableEntity;
import com.workflowtest.server.persistence.mapper.GlobalVariableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class GlobalVariableServiceImpl implements GlobalVariableService {
    private final GlobalVariableMapper globalVariableMapper;
    private final DefinitionPersistenceSupport support;

    @Override
    @Transactional(readOnly = true)
    public List<GlobalVariable> list() {
        return globalVariableMapper.selectList(Wrappers.<GlobalVariableEntity>lambdaQuery()
                        .orderByAsc(GlobalVariableEntity::getVariableKey))
                .stream().map(support::toGlobalVariable).toList();
    }

    @Override
    public GlobalVariable save(GlobalVariable variable) {
        if (variable.key() == null || variable.key().isBlank()) throw new IllegalArgumentException("变量名不能为空");
        GlobalVariableEntity entity = support.blank(variable.id()) ? new GlobalVariableEntity()
                : support.require(globalVariableMapper.selectById(variable.id()), "全局变量不存在");
        entity.setVariableKey(variable.key().trim());
        entity.setValueType(variable.valueType() == null ? "AUTO" : variable.valueType());
        entity.setValueJson(support.serializeVariableValue(variable.value()));
        entity.setEnabled(variable.enabled());
        support.persist(globalVariableMapper, entity, variable.id());
        return support.toGlobalVariable(entity);
    }

    @Override
    public void delete(Long id) {
        globalVariableMapper.deleteById(id);
    }
}
