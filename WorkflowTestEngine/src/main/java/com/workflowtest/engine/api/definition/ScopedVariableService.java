package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.ScopeType;
import com.workflowtest.engine.api.definition.DefinitionModels.ScopedVariable;

import java.util.List;

/**
 * 分层环境变量操作接口（项目 / 组 / 工作流作用域）
 */
public interface ScopedVariableService {
    /**
     * 查询指定作用域下的环境变量
     * @param type 作用域类型
     * @param scopeId 作用域主键（项目 / 组 / 工作流 id）
     * @return 变量列表
     */
    List<ScopedVariable> list(ScopeType type, Long scopeId);

    /**
     * 保存环境变量
     * @param variable 变量定义
     * @return 保存后的变量
     */
    ScopedVariable save(ScopedVariable variable);

    /**
     * 删除环境变量
     * @param id 变量主键
     */
    void delete(Long id);

    /**
     * 删除指定作用域下的全部环境变量
     * @param type 作用域类型
     * @param scopeId 作用域主键
     */
    void deleteByScope(ScopeType type, Long scopeId);
}
