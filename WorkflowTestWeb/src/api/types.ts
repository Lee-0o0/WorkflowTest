export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export interface Project {
  id: number
  name: string
  description?: string
  enabled: boolean
}

export interface Group {
  id: number
  projectId: number
  name: string
  description?: string
  sortOrder: number
  enabled: boolean
}

export interface Workflow {
  id: number
  groupId: number
  name: string
  description?: string
  sortOrder: number
  enabled: boolean
}

export type StepType = 'HTTP' | 'SQL' | 'DELAY' | 'SET_VAR' | 'DELETE_VAR'

export interface Step {
  id: number
  ownerId: number
  code: string
  name: string
  type: StepType
  sortOrder: number
  enabled: boolean
  configJson: string
  extractionJson?: string
  assertionJson?: string
  hookStep: boolean
}

export interface Hook {
  id: number
  groupId: number
  hookType: 'BEFORE_GROUP' | 'AFTER_GROUP'
  enabled: boolean
  steps: Step[]
}

export interface ProjectHook {
  id: number
  projectId: number
  hookType: 'BEFORE_EACH_GROUP'
  enabled: boolean
  steps: Step[]
}

export interface GlobalVariable {
  id: number
  key: string
  valueType: string
  value: unknown
  enabled: boolean
}

export interface ExecutionSummary {
  id: string
  type: string
  targetId: string
  targetName: string
  status: string
  startedAt?: string
  finishedAt?: string
  elapsedMs?: number
  errorMessage?: string
}

export interface StepExecutionDetail {
  id: string
  stepCode: string
  phase: string
  status: string
  requestJson?: string
  responseJson?: string
  outputJson?: string
  extractedJson?: string
  assertionJson?: string
  elapsedMs?: number
  errorMessage?: string
}

export type PrimaryNav = 'projects' | 'globals' | 'history'

export type CreateDialogKind = 'project' | 'group' | 'workflow' | 'step' | 'globalVariable'

export interface CreateDialogState {
  visible: boolean
  kind: CreateDialogKind | null
  title: string
  subtitle?: string
  project?: Project
  group?: Group
  workflow?: Workflow
  stepType?: StepType
  name: string
  description: string
  code: string
  value: string
}

export interface GroupHookTargetDialogState {
  visible: boolean
  project?: Project
  hookType?: 'BEFORE_GROUP' | 'AFTER_GROUP'
  groupId?: number
  groups: Group[]
}

export type SelectionKind = 'none' | 'project' | 'group' | 'workflow' | 'step' | 'hookStep'

export interface AppSelection {
  kind: SelectionKind
  project?: Project
  group?: Group
  workflow?: Workflow
  step?: Step
  hookScope?: 'project' | 'group'
}
