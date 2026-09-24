package com.workflowtest.server.service.support;

import com.workflowtest.server.service.definition.DefinitionModels.EffectiveEnvironment;

/**
 * 环境变量预览接口
 */
public interface EnvironmentPreviewService {
    /**
     * 预览四级环境变量合并结果
     * @param projectId 项目主键，可为空
     * @param groupId 组主键，可为空
     * @param workflowId 工作流主键，可为空
     * @return 各层变量、合并后的有效变量及来源信息
     */
    EffectiveEnvironment preview(Long projectId, Long groupId, Long workflowId);
}
