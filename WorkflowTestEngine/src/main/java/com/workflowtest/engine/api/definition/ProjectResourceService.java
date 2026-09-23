package com.workflowtest.engine.api.definition;

import com.workflowtest.engine.api.definition.DefinitionModels.ProjectResource;
import com.workflowtest.engine.api.definition.DefinitionModels.ProjectResourceType;

import java.util.List;
import java.util.Map;

/**
 * 项目资源操作接口
 */
public interface ProjectResourceService {
    /**
     * 查询项目下的全部资源
     * @param projectId 项目主键
     * @return 资源列表
     */
    List<ProjectResource> list(Long projectId);

    /**
     * 按类型查询项目资源
     * @param projectId 项目主键
     * @param type 资源类型
     * @return 资源列表
     */
    List<ProjectResource> list(Long projectId, ProjectResourceType type);

    /**
     * 保存项目资源
     * @param resource 资源定义
     * @param secret 数据源密码等敏感配置，为空时保留原密码
     * @return 保存后的资源
     */
    ProjectResource save(ProjectResource resource, String secret);

    /**
     * 测试已保存数据源的连接
     * @param id 资源主键
     * @return 连接成功返回 {@code true}
     */
    boolean testConnection(Long id);

    /**
     * 测试数据源连接（可用于新建或编辑前校验）
     * @param config 数据源配置
     * @param username 用户名
     * @param secret 密码，为空时可从已有资源读取
     * @param existingResourceId 已有资源主键，用于复用已保存密码
     */
    void testDatasourceConnection(Map<String, Object> config, String username, String secret, Long existingResourceId);

    /**
     * 删除项目资源
     * @param id 资源主键
     */
    void delete(Long id);

    /**
     * 删除项目下的全部资源
     * @param projectId 项目主键
     */
    void deleteByProject(Long projectId);
}
