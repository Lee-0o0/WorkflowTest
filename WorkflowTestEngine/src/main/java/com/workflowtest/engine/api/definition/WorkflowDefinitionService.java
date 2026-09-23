package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.Workflow;

/**
 * 工作流定义操作接口
 */
public interface WorkflowDefinitionService {
    /**
     * 新建或更新工作流
     * @param id 工作流主键，为空时表示新建
     * @param groupId 所属组主键
     * @param name 工作流名称
     * @param description 工作流说明
     * @param sortOrder 排序序号
     * @return 保存后的工作流
     */
    Workflow save(Long id, Long groupId, String name, String description, int sortOrder);

    /**
     * 调整工作流在组内的显示与执行顺序
     * @param id 工作流主键
     * @param delta 位移量，正数下移、负数上移
     */
    void move(Long id, int delta);

    /**
     * 删除工作流及其步骤与工作流级变量
     * @param id 工作流主键
     */
    void delete(Long id);
}
