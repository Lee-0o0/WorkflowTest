package com.workflowtest.engine.support;

/**
 * 引擎异常与校验消息集中维护。
 */
public final class EngineMessages {
    private EngineMessages() {}

    public static final String EXECUTION_CANCELLED = "执行已取消";

    public static final String FILE_RESOURCE_NOT_FOUND = "文件资源不存在: %s";

    public static final String HTTP_URL_REQUIRED = "HTTP URL 不能为空";
    public static final String VARIABLE_NOT_FOUND = "变量不存在: %s";
    public static final String EXTRACTION_TARGET_PREFIX = "提取目标必须以 %s 开头";
    public static final String EXTRACTION_TARGET_SINGLE_LEVEL = "一期提取目标仅支持单层变量名";
    public static final String ENV_VARIABLE_INVALID = "环境变量格式无效: %s";
    public static final String JSON_ARRAY_INVALID = "JSON 数组格式无效";

    public static final String PACKAGE_EMPTY = "执行包不能为空";
    public static final String PACKAGE_DEFINITION_INVALID = "执行包 definition 无效";
    public static final String PACKAGE_CHECKSUM_MISMATCH = "执行包校验和不匹配，拒绝执行";
    public static final String PACKAGE_CHECKSUM_FAILED = "执行包校验失败";

    public static final String SQL_DATASOURCE_REQUIRED = "SQL 数据源和语句不能为空";
    public static final String SQL_PARAM_NOT_FOUND = "SQL 参数 :%s 在环境变量中不存在";
    public static final String SQL_OPERATION_UNSUPPORTED = "不支持的 SQL 操作: %s";
    public static final String SQL_DANGEROUS_FORBIDDEN = "危险 SQL 默认禁止执行";

    public static final String SET_VAR_VARIABLES_REQUIRED = "环境变量赋值步骤缺少 variables 配置";
    public static final String DELETE_VAR_VARIABLES_REQUIRED = "环境变量删除步骤缺少 variables 配置";
    public static final String RUNTIME_VAR_WORKFLOW_TARGET_ONLY = "工作流步骤仅支持 workflow.* 环境变量目标: %s";
    public static final String RUNTIME_VAR_GROUP_TARGET_ONLY = "组级步骤仅支持 group.* 环境变量目标: %s";
    public static final String RUNTIME_VAR_PROJECT_TARGET_ONLY = "项目钩子仅支持 project.* 环境变量目标: %s";
    /** @deprecated 使用 {@link #RUNTIME_VAR_GROUP_TARGET_ONLY} */
    public static final String SET_VAR_TARGET_GROUP_ONLY = RUNTIME_VAR_GROUP_TARGET_ONLY;

    public static final String ASSERT_OPERATOR_UNSUPPORTED = "不支持的断言操作符: %s";
    public static final String REQUIRED_EXTRACTION_MISSING = "必需提取值不存在: %s";

    public static final String WORKFLOW_NOT_FOUND = "工作流不存在";
    public static final String GROUP_NOT_FOUND = "工作流组不存在";
    public static final String PROJECT_NOT_FOUND = "项目不存在";
}
