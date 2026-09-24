import { computed, inject, provide, reactive, ref } from 'vue'
import { api, defaultStepConfig } from '../api/client'
import type {
  AppSelection,
  CreateDialogKind,
  CreateDialogState,
  ExecutionSummary,
  GlobalVariable,
  Group,
  Hook,
  PrimaryNav,
  Project,
  ProjectHook,
  Step,
  StepExecutionDetail,
  StepType,
  Workflow
} from '../api/types'

const AppStateKey = Symbol('appState')

export function provideAppState() {
  const loading = ref(false)
  const error = ref('')
  const serverStatus = ref('检查中…')
  const lastSavedHint = ref('尚未保存')
  const primaryNav = ref<PrimaryNav>('projects')
  const primaryNavCollapsed = ref(false)
  const projectTreeCollapsed = ref(false)

  const projects = ref<Project[]>([])
  const groupsByProject = ref<Record<number, Group[]>>({})
  const workflowsByGroup = ref<Record<number, Workflow[]>>({})
  const stepsByWorkflow = ref<Record<number, Step[]>>({})
  const groupHooksByGroup = ref<Record<number, Hook[]>>({})
  const projectHooksByProject = ref<Record<number, ProjectHook[]>>({})

  const globalVariables = ref<GlobalVariable[]>([])
  const history = ref<ExecutionSummary[]>([])
  const historyDetail = ref<ExecutionSummary | null>(null)
  const historySteps = ref<StepExecutionDetail[]>([])
  const selectedHistoryStep = ref<StepExecutionDetail | null>(null)

  const expandedProjects = reactive(new Set<number>())
  const expandedGroups = reactive(new Set<number>())
  const selection = ref<AppSelection>({ kind: 'none' })

  const emptyCreateDialog = (): CreateDialogState => ({
    visible: false,
    kind: null,
    title: '',
    subtitle: '',
    name: '',
    description: '',
    code: '',
    value: ''
  })

  const createDialog = ref<CreateDialogState>(emptyCreateDialog())

  const currentProject = computed(() => {
    const s = selection.value
    if (s.project) return s.project
    if (s.group) return projects.value.find((p) => p.id === s.group!.projectId)
    return undefined
  })

  const currentWorkflow = computed(() => selection.value.workflow)

  const statusText = computed(() => {
    const s = selection.value
    if (s.kind === 'workflow' || s.kind === 'step') {
      return `${s.project?.name ?? ''} / ${s.group?.name ?? ''} / ${s.workflow?.name ?? ''}`.replace(/^ \/ |\/ $/g, '')
    }
    if (s.kind === 'group') return `${s.project?.name ?? ''} / ${s.group?.name ?? ''}`
    if (s.kind === 'project') return s.project?.name ?? ''
    return 'TestFlow'
  })

  async function withLoading<T>(task: () => Promise<T>) {
    loading.value = true
    error.value = ''
    try {
      return await task()
    } catch (e) {
      error.value = e instanceof Error ? e.message : String(e)
      throw e
    } finally {
      loading.value = false
    }
  }

  async function refreshHealth() {
    const health = await api.health()
    serverStatus.value = `${health.service ?? 'workflow-test-server'} · ${health.status}`
  }

  async function refreshTree() {
    projects.value = await api.listProjects()
    const groupEntries = await Promise.all(
      projects.value.map(async (project) => [project.id, await api.listGroups(project.id)] as const)
    )
    groupsByProject.value = Object.fromEntries(groupEntries)

    const allGroups = groupEntries.flatMap(([, groups]) => groups)
    const workflowEntries = await Promise.all(
      allGroups.map(async (group) => [group.id, await api.listWorkflows(group.id)] as const)
    )
    workflowsByGroup.value = Object.fromEntries(workflowEntries)

    const allWorkflows = workflowEntries.flatMap(([, workflows]) => workflows)
    const stepEntries = await Promise.all(
      allWorkflows.map(async (workflow) => [workflow.id, await api.listWorkflowSteps(workflow.id)] as const)
    )
    stepsByWorkflow.value = Object.fromEntries(stepEntries)

    const hookEntries = await Promise.all(
      allGroups.map(async (group) => {
        try {
          return [group.id, await api.listGroupHooks(group.id)] as const
        } catch {
          return [group.id, [] as Hook[]] as const
        }
      })
    )
    groupHooksByGroup.value = Object.fromEntries(hookEntries)

    const projectHookEntries = await Promise.all(
      projects.value.map(async (project) => {
        try {
          return [project.id, await api.listProjectHooks(project.id)] as const
        } catch {
          return [project.id, [] as ProjectHook[]] as const
        }
      })
    )
    projectHooksByProject.value = Object.fromEntries(projectHookEntries)

    if (projects.value.length && expandedProjects.size === 0) {
      expandedProjects.add(projects.value[0].id)
    }
  }

  async function refreshGlobalVariables() {
    globalVariables.value = await api.listGlobalVariables()
  }

  async function refreshHistory() {
    history.value = await api.executionHistory()
  }

  async function bootstrap() {
    await withLoading(async () => {
      await refreshHealth()
      await refreshTree()
      await refreshGlobalVariables()
      await refreshHistory()
    })
  }

  function selectProject(project: Project) {
    expandedProjects.add(project.id)
    selection.value = { kind: 'project', project }
  }

  function selectGroup(project: Project, group: Group) {
    expandedProjects.add(project.id)
    expandedGroups.add(group.id)
    selection.value = { kind: 'group', project, group }
  }

  function selectWorkflow(project: Project, group: Group, workflow: Workflow) {
    expandedProjects.add(project.id)
    expandedGroups.add(group.id)
    selection.value = { kind: 'workflow', project, group, workflow }
  }

  function selectStep(project: Project, group: Group, workflow: Workflow, step: Step) {
    selection.value = { kind: 'step', project, group, workflow, step, hookScope: undefined }
  }

  function toggleProject(id: number) {
    if (expandedProjects.has(id)) expandedProjects.delete(id)
    else expandedProjects.add(id)
  }

  function toggleGroup(id: number) {
    if (expandedGroups.has(id)) expandedGroups.delete(id)
    else expandedGroups.add(id)
  }

  function togglePrimaryNavCollapsed() {
    primaryNavCollapsed.value = !primaryNavCollapsed.value
  }

  function toggleProjectTreeCollapsed() {
    projectTreeCollapsed.value = !projectTreeCollapsed.value
  }

  function closeCreateDialog() {
    createDialog.value = emptyCreateDialog()
  }

  function openCreateDialog(kind: CreateDialogKind, init: Partial<CreateDialogState>) {
    createDialog.value = {
      ...emptyCreateDialog(),
      visible: true,
      kind,
      ...init
    }
  }

  function openCreateProjectDialog() {
    openCreateDialog('project', { title: '新建项目', name: '', description: '' })
  }

  function openCreateGroupDialog(project: Project) {
    openCreateDialog('group', {
      title: '新建组',
      subtitle: `所属项目：${project.name}`,
      project,
      name: '',
      description: ''
    })
  }

  function openCreateWorkflowDialog(project: Project, group: Group) {
    openCreateDialog('workflow', {
      title: '新建工作流',
      subtitle: `${project.name} / ${group.name}`,
      project,
      group,
      name: '',
      description: ''
    })
  }

  function openCreateStepDialog(project: Project, group: Group, workflow: Workflow, stepType: StepType) {
    openCreateDialog('step', {
      title: '添加步骤',
      subtitle: workflow.name,
      project,
      group,
      workflow,
      stepType,
      code: `${stepType.toLowerCase()}_step`,
      name: stepType === 'HTTP' ? 'HTTP 请求' : stepType
    })
  }

  function openCreateGlobalVariableDialog() {
    openCreateDialog('globalVariable', { title: '新增全局变量', name: '', value: '""' })
  }

  async function submitCreateDialog() {
    const dialog = createDialog.value
    if (!dialog.kind) return

    const name = dialog.name.trim()
    if (!name) {
      error.value = '名称不能为空'
      return
    }
    if (dialog.kind === 'step' && !dialog.code.trim()) {
      error.value = '步骤编码不能为空'
      return
    }

    await withLoading(async () => {
      switch (dialog.kind) {
        case 'project': {
          const project = await api.createProject(name, dialog.description.trim())
          await refreshTree()
          expandedProjects.add(project.id)
          selectProject(project)
          break
        }
        case 'group': {
          if (!dialog.project) return
          const group = await api.createGroup(dialog.project.id, name, dialog.description.trim())
          await refreshTree()
          selectGroup(dialog.project, group)
          break
        }
        case 'workflow': {
          if (!dialog.project || !dialog.group) return
          const workflow = await api.createWorkflow(dialog.group.id, name, dialog.description.trim())
          await refreshTree()
          selectWorkflow(dialog.project, dialog.group, workflow)
          break
        }
        case 'step': {
          if (!dialog.project || !dialog.group || !dialog.workflow || !dialog.stepType) return
          const code = dialog.code.trim()
          const steps = stepsByWorkflow.value[dialog.workflow.id] ?? []
          const step = await api.createWorkflowStep(dialog.workflow.id, {
            code,
            name,
            type: dialog.stepType,
            sortOrder: steps.length,
            enabled: true,
            configJson: defaultStepConfig(dialog.stepType),
            extractionJson: '[]',
            assertionJson: '[]',
            hookStep: false
          })
          await refreshTree()
          selectStep(dialog.project, dialog.group, dialog.workflow, step)
          break
        }
        case 'globalVariable': {
          let value: unknown = dialog.value
          try {
            value = JSON.parse(dialog.value || '""')
          } catch {
            value = dialog.value
          }
          await api.createGlobalVariable(name, value)
          await refreshGlobalVariables()
          break
        }
      }
      lastSavedHint.value = '已保存'
      closeCreateDialog()
    })
  }

  async function createProject() {
    openCreateProjectDialog()
  }

  async function createGroup(project: Project) {
    openCreateGroupDialog(project)
  }

  async function createWorkflow(project: Project, group: Group) {
    openCreateWorkflowDialog(project, group)
  }

  async function createWorkflowStep(workflow: Workflow, type: StepType, project: Project, group: Group) {
    openCreateStepDialog(project, group, workflow, type)
  }

  async function saveCurrentStep() {
    const s = selection.value
    if (s.kind !== 'step' || !s.step || !s.workflow) return
    await withLoading(async () => {
      await api.updateWorkflowStep(s.step!.id, s.workflow!.id, {
        code: s.step!.code,
        name: s.step!.name,
        type: s.step!.type,
        sortOrder: s.step!.sortOrder,
        enabled: s.step!.enabled,
        configJson: s.step!.configJson,
        extractionJson: s.step!.extractionJson ?? '[]',
        assertionJson: s.step!.assertionJson ?? '[]',
        hookStep: false
      })
      await refreshTree()
      const refreshed = stepsByWorkflow.value[s.workflow!.id]?.find((item) => item.id === s.step!.id)
      if (refreshed) selection.value = { ...s, step: refreshed }
      lastSavedHint.value = `已保存 · ${new Date().toLocaleTimeString()}`
    })
  }

  async function deleteSelection() {
    const s = selection.value
    if (s.kind === 'project' && s.project) {
      if (!window.confirm(`删除项目「${s.project.name}」？`)) return
      await withLoading(async () => {
        await api.deleteProject(s.project!.id)
        selection.value = { kind: 'none' }
        await refreshTree()
      })
      return
    }
    if (s.kind === 'group' && s.group) {
      if (!window.confirm(`删除组「${s.group.name}」？`)) return
      await withLoading(async () => {
        await api.deleteGroup(s.group!.id)
        selection.value = s.project ? { kind: 'project', project: s.project } : { kind: 'none' }
        await refreshTree()
      })
      return
    }
    if (s.kind === 'workflow' && s.workflow) {
      if (!window.confirm(`删除工作流「${s.workflow.name}」？`)) return
      await withLoading(async () => {
        await api.deleteWorkflow(s.workflow!.id)
        if (s.project && s.group) selectGroup(s.project, s.group)
        await refreshTree()
      })
      return
    }
    if (s.kind === 'step' && s.step) {
      if (!window.confirm(`删除步骤「${s.step.name}」？`)) return
      await withLoading(async () => {
        await api.deleteStep(s.step!.id, s.step!.hookStep)
        if (s.project && s.group && s.workflow) selectWorkflow(s.project, s.group, s.workflow)
        await refreshTree()
      })
    }
  }

  async function runProject(project: Project) {
    await withLoading(async () => {
      await api.runProject(project.id)
      await refreshHistory()
      primaryNav.value = 'history'
      historyDetail.value = history.value[0] ?? null
    })
  }

  async function runGroup(group: Group) {
    await withLoading(async () => {
      await api.runGroup(group.id)
      await refreshHistory()
      primaryNav.value = 'history'
      historyDetail.value = history.value[0] ?? null
    })
  }

  async function runWorkflow(workflow: Workflow) {
    await withLoading(async () => {
      await api.runWorkflow(workflow.id)
      await refreshHistory()
      primaryNav.value = 'history'
      historyDetail.value = history.value[0] ?? null
    })
  }

  async function runCurrentWorkflow() {
    const wf = selection.value.workflow
    if (!wf) {
      error.value = '请先选择要执行的工作流'
      return
    }
    await runWorkflow(wf)
  }

  async function openHistoryDetail(item: ExecutionSummary) {
    historyDetail.value = item
    selectedHistoryStep.value = null
    await withLoading(async () => {
      historySteps.value = await api.executionSteps(item.id)
    })
  }

  async function createGroupHook(group: Group, hookType: 'BEFORE_GROUP' | 'AFTER_GROUP') {
    await withLoading(async () => {
      await api.createGroupHook(group.id, hookType)
      await refreshTree()
      lastSavedHint.value = '钩子已创建'
    })
  }

  async function createProjectHook(project: Project) {
    await withLoading(async () => {
      await api.createProjectHook(project.id)
      await refreshTree()
      lastSavedHint.value = '项目钩子已创建'
    })
  }

  const state = {
    loading,
    error,
    serverStatus,
    lastSavedHint,
    primaryNav,
    primaryNavCollapsed,
    projectTreeCollapsed,
    projects,
    groupsByProject,
    workflowsByGroup,
    stepsByWorkflow,
    groupHooksByGroup,
    projectHooksByProject,
    globalVariables,
    history,
    historyDetail,
    historySteps,
    selectedHistoryStep,
    expandedProjects,
    expandedGroups,
    selection,
    createDialog,
    currentProject,
    currentWorkflow,
    statusText,
    bootstrap,
    refreshTree,
    refreshGlobalVariables,
    refreshHistory,
    selectProject,
    selectGroup,
    selectWorkflow,
    selectStep,
    toggleProject,
    toggleGroup,
    togglePrimaryNavCollapsed,
    toggleProjectTreeCollapsed,
    createProject,
    createGroup,
    createWorkflow,
    createWorkflowStep,
    saveCurrentStep,
    deleteSelection,
    runProject,
    runGroup,
    runWorkflow,
    runCurrentWorkflow,
    openHistoryDetail,
    createGroupHook,
    createProjectHook,
    openCreateProjectDialog,
    openCreateGroupDialog,
    openCreateWorkflowDialog,
    openCreateStepDialog,
    openCreateGlobalVariableDialog,
    closeCreateDialog,
    submitCreateDialog,
    withLoading
  }

  provide(AppStateKey, state)
  return state
}

export function useAppState() {
  const state = inject<ReturnType<typeof provideAppState>>(AppStateKey)
  if (!state) throw new Error('AppState 未初始化')
  return state
}

export type AppState = ReturnType<typeof provideAppState>
