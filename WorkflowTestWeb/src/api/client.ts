import axios from 'axios'
import type { ApiResponse, ExecutionSummary, GlobalVariable, Group, Project, Step, Workflow } from './types'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000
})

async function unwrap<T>(promise: Promise<{ data: ApiResponse<T> }>): Promise<T> {
  const { data } = await promise
  if (!data.success) {
    throw new Error(data.message || '请求失败')
  }
  return data.data
}

export const api = {
  health: () => unwrap(http.get<ApiResponse<{ status: string }>>('/health')),

  listProjects: () => unwrap(http.get<ApiResponse<Project[]>>('/projects')),
  createProject: (name: string, description = '') =>
    unwrap(http.post<ApiResponse<Project>>('/projects', { name, description })),
  updateProject: (id: number, name: string, description = '') =>
    unwrap(http.put<ApiResponse<Project>>(`/projects/${id}`, { name, description })),
  deleteProject: (id: number) => unwrap(http.delete<ApiResponse<void>>(`/projects/${id}`)),

  listGroups: (projectId: number) => unwrap(http.get<ApiResponse<Group[]>>(`/projects/${projectId}/groups`)),
  createGroup: (projectId: number, name: string, description = '') =>
    unwrap(http.post<ApiResponse<Group>>('/groups', { projectId, name, description, sortOrder: 0 })),
  updateGroup: (id: number, projectId: number, name: string, description = '') =>
    unwrap(http.put<ApiResponse<Group>>(`/groups/${id}`, { projectId, name, description, sortOrder: 0 })),
  deleteGroup: (id: number) => unwrap(http.delete<ApiResponse<void>>(`/groups/${id}`)),

  listWorkflows: (groupId: number) => unwrap(http.get<ApiResponse<Workflow[]>>(`/groups/${groupId}/workflows`)),
  createWorkflow: (groupId: number, name: string, description = '') =>
    unwrap(http.post<ApiResponse<Workflow>>('/workflows', { groupId, name, description, sortOrder: 0 })),
  updateWorkflow: (id: number, groupId: number, name: string, description = '') =>
    unwrap(http.put<ApiResponse<Workflow>>(`/workflows/${id}`, { groupId, name, description, sortOrder: 0 })),
  deleteWorkflow: (id: number) => unwrap(http.delete<ApiResponse<void>>(`/workflows/${id}`)),

  listWorkflowSteps: (workflowId: number) =>
    unwrap(http.get<ApiResponse<Step[]>>(`/workflows/${workflowId}/steps`)),
  createWorkflowStep: (workflowId: number, payload: Partial<Step>) =>
    unwrap(http.post<ApiResponse<Step>>(`/workflows/${workflowId}/steps`, payload)),
  deleteStep: (id: number, hookStep = false) =>
    unwrap(http.delete<ApiResponse<void>>(hookStep ? `/hook-steps/${id}` : `/steps/${id}`)),

  listGlobalVariables: () => unwrap(http.get<ApiResponse<GlobalVariable[]>>('/global-variables')),
  createGlobalVariable: (key: string, value: unknown) =>
    unwrap(http.post<ApiResponse<GlobalVariable>>('/global-variables', { key, valueType: 'AUTO', value, enabled: true })),
  deleteGlobalVariable: (id: number) => unwrap(http.delete<ApiResponse<void>>(`/global-variables/${id}`)),

  runProject: (projectId: number) =>
    unwrap(http.post<ApiResponse<{ executionId: string }>>(`/executions/projects/${projectId}`, {})),
  runGroup: (groupId: number) =>
    unwrap(http.post<ApiResponse<{ executionId: string }>>(`/executions/groups/${groupId}`, {})),
  runWorkflow: (workflowId: number) =>
    unwrap(http.post<ApiResponse<{ executionId: string }>>(`/executions/workflows/${workflowId}`, {})),
  cancelExecution: (executionId: string) =>
    unwrap(http.post<ApiResponse<void>>(`/executions/${executionId}/cancel`)),
  executionHistory: (limit = 50) =>
    unwrap(http.get<ApiResponse<ExecutionSummary[]>>(`/executions/history?limit=${limit}`))
}
