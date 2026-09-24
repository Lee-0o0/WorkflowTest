package com.workflowtest.engine.model.plan;

/**
 * 执行计划中的实体引用（项目 / 组 / 工作流）。
 */
public record PlanEntityRef(Long id, String name) {
    public String idText() {
        return id == null ? "0" : String.valueOf(id);
    }
}
