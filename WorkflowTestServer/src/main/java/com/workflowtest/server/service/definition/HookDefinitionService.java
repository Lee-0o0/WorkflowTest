package com.workflowtest.server.service.definition;

import com.workflowtest.server.service.definition.DefinitionModels.Hook;
import com.workflowtest.server.service.definition.DefinitionModels.HookType;
import com.workflowtest.server.service.definition.DefinitionModels.Step;

import java.util.List;
import java.util.Optional;

/**
 * 组级钩子定义操作接口
 */
public interface HookDefinitionService {
    /**
     * 查询钩子
     * @param groupId 钩子所属组ID
     * @param hookType 钩子类型：分为前置钩子和后置钩子
     * @return 钩子详情（不含步骤）
     */
    Optional<Hook> find(Long groupId, HookType hookType);

    /**
     * 创建钩子
     * @param groupId 钩子所属组ID
     * @param hookType 钩子类型：分为前置钩子和后置钩子
     * @return 创建好的钩子，如果钩子已存在，会抛出异常
     */
    Hook create(Long groupId, HookType hookType);

    /**
     * 查询组的钩子
     * @param groupId 组ID
     * @return 该组的所有钩子（不含步骤）
     */
    List<Hook> listByGroup(Long groupId);

    /**
     * 查询钩子下的步骤
     * @param hookId 钩子主键
     * @return 步骤列表
     */
    List<Step> listSteps(Long hookId);

    /**
     * 删除属于该组的钩子
     * @param groupId 组ID
     */
    void deleteByGroup(Long groupId);
}
