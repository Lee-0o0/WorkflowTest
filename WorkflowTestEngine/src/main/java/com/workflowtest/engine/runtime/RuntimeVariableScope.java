package com.workflowtest.engine.runtime;

/** 运行时环境变量读写允许的作用域。 */
public enum RuntimeVariableScope {
    /** 工作流步骤：仅工作流运行时变量，工作流结束后销毁。 */
    WORKFLOW,
    /** 组钩子：仅组运行时变量，组运行结束后销毁。 */
    GROUP,
    /** 项目钩子：仅项目运行时变量，每个组运行结束后销毁。 */
    PROJECT
}
