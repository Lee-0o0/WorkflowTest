package com.workflowtest.server.service.impl.execution;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflowtest.engine.model.EffectiveEnvironment;
import com.workflowtest.engine.model.StepType;
import com.workflowtest.engine.model.plan.GroupExecutionPlan;
import com.workflowtest.engine.model.plan.HookPlan;
import com.workflowtest.engine.model.plan.PlanEntityRef;
import com.workflowtest.engine.model.plan.ProjectExecutionPlan;
import com.workflowtest.engine.model.plan.RuntimeDataSourcePlan;
import com.workflowtest.engine.model.plan.RuntimeFilePlan;
import com.workflowtest.engine.model.plan.RuntimeProjectResources;
import com.workflowtest.engine.model.plan.WorkflowExecutionPlan;
import com.workflowtest.engine.runtime.RuntimeStep;
import com.workflowtest.server.service.definition.DefinitionModels.ProjectResourceType;
import com.workflowtest.server.persistence.entity.*;
import com.workflowtest.server.persistence.mapper.*;
import com.workflowtest.server.runtime.EnvironmentResolver;
import com.workflowtest.server.security.SecretCipher;
import com.workflowtest.server.support.JdbcConnectionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionPlanAssembler {
    private final ProjectMapper projectMapper;
    private final WorkflowGroupMapper groupMapper;
    private final WorkflowMapper workflowMapper;
    private final StepMapper stepMapper;
    private final HookMapper hookMapper;
    private final HookStepMapper hookStepMapper;
    private final ProjectResourceMapper projectResourceMapper;
    private final ProjectHookMapper projectHookMapper;
    private final ProjectHookStepMapper projectHookStepMapper;
    private final EnvironmentResolver environmentResolver;
    private final SecretCipher secretCipher;
    private final ObjectMapper objectMapper;

    public ProjectExecutionPlan buildProjectPlan(Long projectId) {
        ProjectEntity project = required(projectMapper.selectById(projectId), "项目不存在");
        List<GroupExecutionPlan> groups = groupMapper.selectList(Wrappers.<WorkflowGroupEntity>lambdaQuery()
                        .eq(WorkflowGroupEntity::getProjectId, projectId)
                        .eq(WorkflowGroupEntity::getEnabled, true)
                        .orderByAsc(WorkflowGroupEntity::getSortOrder))
                .stream()
                .map(group -> buildGroupPlan(group.getId()))
                .toList();
        return new ProjectExecutionPlan(
                entityRef(project),
                toEngineEnvironment(environmentResolver.resolve(projectId, null, null)),
                projectResources(projectId),
                projectHook("BEFORE_EACH_GROUP", projectId),
                groups);
    }

    public GroupExecutionPlan buildGroupPlan(Long groupId) {
        WorkflowGroupEntity group = required(groupMapper.selectById(groupId), "工作流组不存在");
        ProjectEntity project = required(projectMapper.selectById(group.getProjectId()), "项目不存在");
        List<WorkflowExecutionPlan> workflows = workflowMapper.selectList(Wrappers.<WorkflowEntity>lambdaQuery()
                        .eq(WorkflowEntity::getGroupId, groupId)
                        .eq(WorkflowEntity::getEnabled, true)
                        .orderByAsc(WorkflowEntity::getSortOrder))
                .stream()
                .map(workflow -> buildWorkflowFragment(workflow.getId()))
                .toList();
        return new GroupExecutionPlan(
                entityRef(group),
                toEngineEnvironment(environmentResolver.resolve(project.getId(), group.getId(), null)),
                projectResources(project.getId()),
                groupHook("BEFORE_GROUP", groupId),
                groupHook("AFTER_GROUP", groupId),
                workflows);
    }

    public WorkflowExecutionPlan buildWorkflowPlan(Long workflowId) {
        WorkflowEntity workflow = required(workflowMapper.selectById(workflowId), "工作流不存在");
        WorkflowGroupEntity group = required(groupMapper.selectById(workflow.getGroupId()), "工作流组不存在");
        ProjectEntity project = required(projectMapper.selectById(group.getProjectId()), "项目不存在");
        return new WorkflowExecutionPlan(
                entityRef(workflow),
                toEngineEnvironment(environmentResolver.resolve(project.getId(), group.getId(), workflow.getId())),
                projectResources(project.getId()),
                workflowSteps(workflowId));
    }

    public WorkflowExecutionPlan buildWorkflowFragment(Long workflowId) {
        WorkflowEntity workflow = required(workflowMapper.selectById(workflowId), "工作流不存在");
        WorkflowGroupEntity group = required(groupMapper.selectById(workflow.getGroupId()), "工作流组不存在");
        ProjectEntity project = required(projectMapper.selectById(group.getProjectId()), "项目不存在");
        return new WorkflowExecutionPlan(
                entityRef(workflow),
                toEngineEnvironment(environmentResolver.resolve(project.getId(), group.getId(), workflow.getId())),
                projectResources(project.getId()),
                workflowSteps(workflowId));
    }

    private HookPlan groupHook(String hookType, Long groupId) {
        HookEntity hook = hookMapper.selectOne(Wrappers.<HookEntity>lambdaQuery()
                .eq(HookEntity::getGroupId, groupId)
                .eq(HookEntity::getHookType, hookType)
                .eq(HookEntity::getEnabled, true));
        return hook == null ? null : new HookPlan(hook.getId(), hookSteps(hook.getId()));
    }

    private HookPlan projectHook(String hookType, Long projectId) {
        ProjectHookEntity hook = projectHookMapper.selectOne(Wrappers.<ProjectHookEntity>lambdaQuery()
                .eq(ProjectHookEntity::getProjectId, projectId)
                .eq(ProjectHookEntity::getHookType, hookType)
                .eq(ProjectHookEntity::getEnabled, true));
        return hook == null ? null : new HookPlan(hook.getId(), projectHookSteps(hook.getId()));
    }

    private List<RuntimeStep> workflowSteps(Long workflowId) {
        return stepMapper.selectList(Wrappers.<StepEntity>lambdaQuery()
                        .eq(StepEntity::getWorkflowId, workflowId)
                        .eq(StepEntity::getEnabled, true)
                        .orderByAsc(StepEntity::getSortOrder))
                .stream()
                .map(this::toRuntimeStep)
                .sorted(Comparator.comparingInt(RuntimeStep::order))
                .toList();
    }

    private List<RuntimeStep> hookSteps(Long hookId) {
        return hookStepMapper.selectList(Wrappers.<HookStepEntity>lambdaQuery()
                        .eq(HookStepEntity::getHookId, hookId)
                        .eq(HookStepEntity::getEnabled, true)
                        .orderByAsc(HookStepEntity::getSortOrder))
                .stream()
                .map(this::toRuntimeStep)
                .sorted(Comparator.comparingInt(RuntimeStep::order))
                .toList();
    }

    private List<RuntimeStep> projectHookSteps(Long projectHookId) {
        return projectHookStepMapper.selectList(Wrappers.<ProjectHookStepEntity>lambdaQuery()
                        .eq(ProjectHookStepEntity::getProjectHookId, projectHookId)
                        .eq(ProjectHookStepEntity::getEnabled, true)
                        .orderByAsc(ProjectHookStepEntity::getSortOrder))
                .stream()
                .map(this::toRuntimeStep)
                .sorted(Comparator.comparingInt(RuntimeStep::order))
                .toList();
    }

    private RuntimeStep toRuntimeStep(StepEntity step) {
        return new RuntimeStep(
                step.getId(),
                step.getStepCode(),
                step.getStepName(),
                StepType.valueOf(step.getStepType()),
                step.getSortOrder() == null ? 0 : step.getSortOrder(),
                step.getConfigJson(),
                step.getExtractionJson(),
                step.getAssertionJson());
    }

    private RuntimeStep toRuntimeStep(HookStepEntity step) {
        return new RuntimeStep(
                step.getId(),
                step.getStepCode(),
                step.getStepName(),
                StepType.valueOf(step.getStepType()),
                step.getSortOrder() == null ? 0 : step.getSortOrder(),
                step.getConfigJson(),
                step.getExtractionJson(),
                step.getAssertionJson());
    }

    private RuntimeStep toRuntimeStep(ProjectHookStepEntity step) {
        return new RuntimeStep(
                step.getId(),
                step.getStepCode(),
                step.getStepName(),
                StepType.valueOf(step.getStepType()),
                step.getSortOrder() == null ? 0 : step.getSortOrder(),
                step.getConfigJson(),
                step.getExtractionJson(),
                step.getAssertionJson());
    }

    private RuntimeProjectResources projectResources(Long projectId) {
        List<RuntimeDataSourcePlan> datasources = new ArrayList<>();
        List<RuntimeFilePlan> files = new ArrayList<>();
        projectResourceMapper.selectList(Wrappers.<ProjectResourceEntity>lambdaQuery()
                        .eq(ProjectResourceEntity::getProjectId, projectId)
                        .eq(ProjectResourceEntity::getEnabled, true))
                .forEach(resource -> {
                    try {
                        Map<String, Object> config = objectMapper.readValue(resource.getConfigJson(), Map.class);
                        ProjectResourceType type = ProjectResourceType.valueOf(resource.getResourceType());
                        if (type == ProjectResourceType.DATASOURCE) {
                            JdbcConnectionConfig.enrichFromJdbcUrl(config);
                            datasources.add(new RuntimeDataSourcePlan(
                                    resource.getId(),
                                    String.valueOf(config.getOrDefault("driverClass", "")),
                                    JdbcConnectionConfig.resolveJdbcUrl(config),
                                    String.valueOf(config.getOrDefault("username", "")),
                                    secretCipher.decrypt(String.valueOf(config.getOrDefault("encryptedPassword", ""))),
                                    Boolean.TRUE.equals(config.get("allowDangerousSql"))));
                        } else if (type == ProjectResourceType.FILE) {
                            files.add(new RuntimeFilePlan(
                                    resource.getId(),
                                    resource.getName(),
                                    String.valueOf(config.getOrDefault("path", "")),
                                    String.valueOf(config.getOrDefault("encoding", "UTF-8"))));
                        }
                    } catch (Exception e) {
                        throw new IllegalArgumentException("项目资源配置无效: " + resource.getName(), e);
                    }
                });
        return new RuntimeProjectResources(List.copyOf(datasources), List.copyOf(files));
    }

    private EffectiveEnvironment toEngineEnvironment(
            com.workflowtest.server.service.definition.DefinitionModels.EffectiveEnvironment environment) {
        return new EffectiveEnvironment(
                environment.global(),
                environment.project(),
                environment.group(),
                environment.workflow(),
                environment.effective(),
                environment.sources());
    }

    private PlanEntityRef entityRef(ProjectEntity entity) {
        return new PlanEntityRef(entity.getId(), entity.getName());
    }

    private PlanEntityRef entityRef(WorkflowGroupEntity entity) {
        return new PlanEntityRef(entity.getId(), entity.getName());
    }

    private PlanEntityRef entityRef(WorkflowEntity entity) {
        return new PlanEntityRef(entity.getId(), entity.getName());
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
