package com.workflowtest.engine.executor.support;

import com.workflowtest.engine.model.plan.RuntimeProjectResources;
import lombok.RequiredArgsConstructor;

/**
 * 执行期项目资源统一入口：合并/清理数据源与文件等资源。
 */
@RequiredArgsConstructor
public class RuntimeResourceManager {
    private final RuntimeDataSourceManager dataSourceManager;
    private final RuntimeFileResourceManager fileResourceManager;

    public void merge(RuntimeProjectResources resources) {
        if (resources == null) {
            return;
        }
        dataSourceManager.merge(resources.datasources());
        fileResourceManager.merge(resources.files());
    }

    public void clear() {
        dataSourceManager.clear();
        fileResourceManager.clear();
    }

    public RuntimeDataSourceManager datasources() {
        return dataSourceManager;
    }

    public RuntimeFileResourceManager files() {
        return fileResourceManager;
    }
}
