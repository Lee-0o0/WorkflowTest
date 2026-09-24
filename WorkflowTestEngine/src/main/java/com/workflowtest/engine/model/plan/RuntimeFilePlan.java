package com.workflowtest.engine.model.plan;

/**
 * 执行计划中的文件资源定义。
 */
public record RuntimeFilePlan(
        long id,
        String name,
        String path,
        String encoding) {
}
