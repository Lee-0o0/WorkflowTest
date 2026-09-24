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

export interface Step {
  id: number
  ownerId: number
  code: string
  name: string
  type: 'HTTP' | 'SQL' | 'DELAY' | 'SET_VAR' | 'DELETE_VAR'
  sortOrder: number
  enabled: boolean
  configJson: string
  extractionJson?: string
  assertionJson?: string
  hookStep: boolean
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

export type TreeNodeType = 'root' | 'project' | 'group' | 'workflow' | 'step' | 'global' | 'history'

export interface TreeSelection {
  type: TreeNodeType
  project?: Project
  group?: Group
  workflow?: Workflow
  step?: Step
}
