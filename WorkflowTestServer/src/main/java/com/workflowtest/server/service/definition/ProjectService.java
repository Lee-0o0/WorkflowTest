package com.workflowtest.server.service.definition;

import com.workflowtest.server.service.definition.DefinitionModels.Project;

/**
 * 测试项目操作接口
 */
public interface ProjectService {
    /**
     * 新建或更新项目
     * @param id 项目主键，为空时表示新建
     * @param name 项目名称
     * @param description 项目说明
     * @return 保存后的项目
     */
    Project save(Long id, String name, String description);

    /**
     * 删除项目及其下属组、工作流、变量与资源
     * @param id 项目主键
     */
    void delete(Long id);
}
