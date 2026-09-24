package com.workflowtest.engine.executor.support;

/**
 * 执行期文件资源运行时定义。
 */
public record FileRuntimeDefinition(
        long id,
        String name,
        String path,
        String encoding) {
}
