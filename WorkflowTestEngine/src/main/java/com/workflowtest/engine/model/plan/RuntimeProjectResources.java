package com.workflowtest.engine.model.plan;

import java.util.List;

/**
 * 执行计划携带的项目级运行时资源（数据源、文件等）。
 */
public record RuntimeProjectResources(
        List<RuntimeDataSourcePlan> datasources,
        List<RuntimeFilePlan> files) {

    public RuntimeProjectResources {
        datasources = datasources == null ? List.of() : List.copyOf(datasources);
        files = files == null ? List.of() : List.copyOf(files);
    }

    public static RuntimeProjectResources empty() {
        return new RuntimeProjectResources(List.of(), List.of());
    }
}
