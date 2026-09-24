package com.workflowtest.server.service.definition;

import com.workflowtest.server.service.definition.DefinitionModels.GlobalVariable;

import java.util.List;

/**
 * 全局变量操作接口
 */
public interface GlobalVariableService {
    /**
     * 查询所有全局变量
     * @return
     */
    List<GlobalVariable> list();

    /**
     * 保存全局变量
     * @param variable
     * @return
     */
    GlobalVariable save(GlobalVariable variable);

    /**
     * 删除全局变量
     * @param id
     */
    void delete(Long id);
}
