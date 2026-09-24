package com.workflowtest.server.service.definition;

import com.workflowtest.server.service.definition.DefinitionModels.Step;

/**
 * 步骤定义操作接口（工作流步骤与钩子步骤）
 */
public interface StepDefinitionService {
    /**
     * 保存工作流步骤
     * @param step 步骤定义，{@code ownerId} 为工作流主键
     * @return 保存后的步骤
     */
    Step saveWorkflowStep(Step step);

    /**
     * 保存钩子步骤
     * @param hookId 钩子主键
     * @param step 步骤定义
     * @return 保存后的步骤
     */
    Step saveHookStep(Long hookId, Step step);

    /**
     * 调整步骤执行顺序
     * @param id 步骤主键
     * @param hookStep 是否为钩子步骤
     * @param delta 位移量，正数下移、负数上移
     */
    void move(Long id, boolean hookStep, int delta);

    /**
     * 删除步骤
     * @param id 步骤主键
     * @param hookStep 是否为钩子步骤
     */
    void delete(Long id, boolean hookStep);
}
