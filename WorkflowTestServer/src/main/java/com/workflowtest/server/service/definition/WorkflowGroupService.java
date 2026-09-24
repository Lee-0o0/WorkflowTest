package com.workflowtest.server.service.definition;

import com.workflowtest.server.service.definition.DefinitionModels.Group;

/**
 * 工作流组操作接口
 */
public interface WorkflowGroupService {
    /**
     * 新建或更新组；新建时会自动创建组后置钩子
     * @param id 组主键，为空时表示新建
     * @param projectId 所属项目主键
     * @param name 组名称
     * @param description 组说明
     * @param sortOrder 排序序号
     * @return 保存后的组
     */
    Group save(Long id, Long projectId, String name, String description, int sortOrder);

    /**
     * 调整组在项目内的显示与执行顺序
     * @param id 组主键
     * @param delta 位移量，正数下移、负数上移
     */
    void move(Long id, int delta);

    /**
     * 删除组及其下属工作流、钩子与组级变量
     * @param id 组主键
     */
    void delete(Long id);
}
