package com.workflowtest.engine.executor.config;

/**
 * 步骤 configJson 解析后的强类型配置。
 */
public sealed interface StepConfig permits HttpStepConfig, SqlStepConfig, DelayStepConfig, SetVarStepConfig, DeleteVarStepConfig {
}
