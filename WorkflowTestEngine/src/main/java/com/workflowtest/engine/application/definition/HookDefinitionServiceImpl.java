package com.workflowtest.engine.application.definition;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflowtest.engine.api.definition.DefinitionModels.*;
import com.workflowtest.engine.api.definition.HookDefinitionService;
import com.workflowtest.engine.application.support.DefinitionPersistenceSupport;
import com.workflowtest.engine.persistence.entity.HookEntity;
import com.workflowtest.engine.persistence.entity.HookStepEntity;
import com.workflowtest.engine.persistence.mapper.HookMapper;
import com.workflowtest.engine.persistence.mapper.HookStepMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class HookDefinitionServiceImpl implements HookDefinitionService {
    private final HookMapper hookMapper;
    private final HookStepMapper hookStepMapper;
    private final DefinitionPersistenceSupport support;

    @Override
    @Transactional(readOnly = true)
    public Optional<Hook> find(Long groupId, HookType hookType) {
        HookEntity entity = hookMapper.selectOne(Wrappers.<HookEntity>lambdaQuery()
                .eq(HookEntity::getGroupId, groupId)
                .eq(HookEntity::getHookType, hookType.name()));
        return entity == null ? Optional.empty() : Optional.of(toHook(entity));
    }

    @Override
    public Hook create(Long groupId, HookType hookType) {
        if (find(groupId, hookType).isPresent()) {
            throw new IllegalStateException("钩子已存在: groupId=" + groupId + ", hookType=" + hookType);
        }
        HookEntity entity = new HookEntity();
        entity.setGroupId(groupId);
        entity.setHookType(hookType.name());
        entity.setEnabled(true);
        hookMapper.insert(entity);
        return toHook(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Hook> listByGroup(Long groupId) {
        return hookMapper.selectList(Wrappers.<HookEntity>lambdaQuery()
                        .eq(HookEntity::getGroupId, groupId)
                        .orderByAsc(HookEntity::getHookType))
                .stream().map(this::toHook).toList();
    }

    @Override
    public void deleteByGroup(Long groupId) {
        hookMapper.selectList(Wrappers.<HookEntity>lambdaQuery().eq(HookEntity::getGroupId, groupId))
                .forEach(hook -> {
                    hookStepMapper.delete(Wrappers.<HookStepEntity>lambdaQuery().eq(HookStepEntity::getHookId, hook.getId()));
                    hookMapper.deleteById(hook.getId());
                });
    }

    private Hook toHook(HookEntity entity) {
        List<Step> steps = hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                        .eq(HookStepEntity::getHookId, entity.getId())
                        .orderByAsc(HookStepEntity::getSortOrder))
                .stream().map(support::toHookStep).toList();
        return support.toHook(entity, steps);
    }
}
