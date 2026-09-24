<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from './api/client'
import type { ExecutionSummary, GlobalVariable, Group, Project, Step, TreeSelection, Workflow } from './api/types'

type SidebarItem =
  | { kind: 'nav'; key: 'global' | 'history'; label: string }
  | { kind: 'project'; project: Project }
  | { kind: 'group'; group: Group; project: Project }
  | { kind: 'workflow'; workflow: Workflow; group: Group; project: Project }
  | { kind: 'step'; step: Step; workflow: Workflow; group: Group; project: Project }

const loading = ref(false)
const error = ref('')
const serverStatus = ref('检查中...')
const projects = ref<Project[]>([])
const groupsByProject = ref<Record<number, Group[]>>({})
const workflowsByGroup = ref<Record<number, Workflow[]>>({})
const stepsByWorkflow = ref<Record<number, Step[]>>({})
const globalVariables = ref<GlobalVariable[]>([])
const history = ref<ExecutionSummary[]>([])
const activeKey = ref('nav:global')
const selection = ref<TreeSelection>({ type: 'global' })

const sidebarItems = computed(() => {
  const items: SidebarItem[] = [
    { kind: 'nav', key: 'global', label: '全局环境变量' },
    { kind: 'nav', key: 'history', label: '执行历史' }
  ]
  for (const project of projects.value) {
    items.push({ kind: 'project', project })
    for (const group of groupsByProject.value[project.id] || []) {
      items.push({ kind: 'group', group, project })
      for (const workflow of workflowsByGroup.value[group.id] || []) {
        items.push({ kind: 'workflow', workflow, group, project })
        for (const step of stepsByWorkflow.value[workflow.id] || []) {
          items.push({ kind: 'step', step, workflow, group, project })
        }
      }
    }
  }
  return items
})

function itemKey(item: SidebarItem) {
  switch (item.kind) {
    case 'nav':
      return `nav:${item.key}`
    case 'project':
      return `project:${item.project.id}`
    case 'group':
      return `group:${item.group.id}`
    case 'workflow':
      return `workflow:${item.workflow.id}`
    case 'step':
      return `step:${item.step.id}`
  }
}

function selectItem(item: SidebarItem) {
  activeKey.value = itemKey(item)
  if (item.kind === 'nav') {
    selection.value = { type: item.key }
    return
  }
  if (item.kind === 'project') {
    selection.value = { type: 'project', project: item.project }
    return
  }
  if (item.kind === 'group') {
    selection.value = { type: 'group', project: item.project, group: item.group }
    return
  }
  if (item.kind === 'workflow') {
    selection.value = {
      type: 'workflow',
      project: item.project,
      group: item.group,
      workflow: item.workflow
    }
    return
  }
  selection.value = {
    type: 'step',
    project: item.project,
    group: item.group,
    workflow: item.workflow,
    step: item.step
  }
}

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
  serverStatus.value = `本地服务 ${health.status} · SQLite`
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

async function createProject() {
  const name = window.prompt('项目名称')
  if (!name?.trim()) return
  await withLoading(async () => {
    await api.createProject(name.trim())
    await refreshTree()
  })
}

async function createGroup() {
  if (selection.value.type !== 'project' || !selection.value.project) return
  const name = window.prompt('组名称')
  if (!name?.trim()) return
  await withLoading(async () => {
    await api.createGroup(selection.value.project!.id, name.trim())
    await refreshTree()
  })
}

async function createWorkflow() {
  if (selection.value.type !== 'group' || !selection.value.group) return
  const name = window.prompt('工作流名称')
  if (!name?.trim()) return
  await withLoading(async () => {
    await api.createWorkflow(selection.value.group!.id, name.trim())
    await refreshTree()
  })
}

async function createDelayStep() {
  if (selection.value.type !== 'workflow' || !selection.value.workflow) return
  const code = window.prompt('步骤编码', 'delay_step')
  if (!code?.trim()) return
  await withLoading(async () => {
    await api.createWorkflowStep(selection.value.workflow!.id, {
      code: code.trim(),
      name: '延迟步骤',
      type: 'DELAY',
      sortOrder: (stepsByWorkflow.value[selection.value.workflow!.id] || []).length,
      enabled: true,
      configJson: JSON.stringify({ millis: 1000 }),
      hookStep: false
    })
    await refreshTree()
  })
}

async function createSetVarStep() {
  if (selection.value.type !== 'workflow' || !selection.value.workflow) return
  const code = window.prompt('步骤编码', 'set_var_step')
  if (!code?.trim()) return
  await withLoading(async () => {
    await api.createWorkflowStep(selection.value.workflow!.id, {
      code: code.trim(),
      name: '环境变量赋值',
      type: 'SET_VAR',
      sortOrder: (stepsByWorkflow.value[selection.value.workflow!.id] || []).length,
      enabled: true,
      configJson: JSON.stringify({
        variables: {
          exampleKey: 'exampleValue'
        }
      }),
      hookStep: false
    })
    await refreshTree()
  })
}

async function createDeleteVarStep() {
  if (selection.value.type !== 'workflow' || !selection.value.workflow) return
  const code = window.prompt('步骤编码', 'delete_var_step')
  if (!code?.trim()) return
  await withLoading(async () => {
    await api.createWorkflowStep(selection.value.workflow!.id, {
      code: code.trim(),
      name: '删除环境变量',
      type: 'DELETE_VAR',
      sortOrder: (stepsByWorkflow.value[selection.value.workflow!.id] || []).length,
      enabled: true,
      configJson: JSON.stringify({
        variables: ['exampleKey', 'group.exampleKey']
      }),
      hookStep: false
    })
    await refreshTree()
  })
}

async function createGlobalVariable() {
  const key = window.prompt('变量名')
  if (!key?.trim()) return
  const value = window.prompt('变量值', '')
  await withLoading(async () => {
    await api.createGlobalVariable(key.trim(), value ?? '')
    await refreshGlobalVariables()
    selection.value = { type: 'global' }
    activeKey.value = 'nav:global'
  })
}

async function deleteCurrent() {
  const current = selection.value
  if (current.type === 'project' && current.project) {
    if (!window.confirm(`删除项目「${current.project.name}」？`)) return
    await withLoading(async () => {
      await api.deleteProject(current.project!.id)
      selection.value = { type: 'global' }
      activeKey.value = 'nav:global'
      await refreshTree()
    })
    return
  }
  if (current.type === 'group' && current.group) {
    if (!window.confirm(`删除组「${current.group.name}」？`)) return
    await withLoading(async () => {
      await api.deleteGroup(current.group!.id)
      selection.value = { type: 'project', project: current.project }
      activeKey.value = `project:${current.project!.id}`
      await refreshTree()
    })
    return
  }
  if (current.type === 'workflow' && current.workflow) {
    if (!window.confirm(`删除工作流「${current.workflow.name}」？`)) return
    await withLoading(async () => {
      await api.deleteWorkflow(current.workflow!.id)
      selection.value = { type: 'group', project: current.project, group: current.group }
      activeKey.value = `group:${current.group!.id}`
      await refreshTree()
    })
    return
  }
  if (current.type === 'step' && current.step) {
    if (!window.confirm(`删除步骤「${current.step.name}」？`)) return
    await withLoading(async () => {
      await api.deleteStep(current.step!.id, current.step!.hookStep)
      selection.value = { type: 'workflow', project: current.project, group: current.group, workflow: current.workflow }
      activeKey.value = `workflow:${current.workflow!.id}`
      await refreshTree()
    })
  }
}

async function runCurrent() {
  const current = selection.value
  await withLoading(async () => {
    if (current.type === 'project' && current.project) {
      await api.runProject(current.project.id)
    } else if (current.type === 'group' && current.group) {
      await api.runGroup(current.group.id)
    } else if ((current.type === 'workflow' || current.type === 'step') && current.workflow) {
      await api.runWorkflow(current.workflow.id)
    } else {
      throw new Error('请选择项目、组或工作流后再执行')
    }
    await refreshHistory()
    selection.value = { type: 'history' }
    activeKey.value = 'nav:history'
  })
}

onMounted(() => {
  bootstrap().catch(() => undefined)
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div>
        <h1>WorkflowTest</h1>
        <div class="status-badge">{{ serverStatus }}</div>
      </div>
      <div class="actions">
        <button class="btn" :disabled="loading" @click="bootstrap">刷新</button>
        <button class="btn btn-primary" :disabled="loading" @click="createProject">新建项目</button>
      </div>
    </header>

    <div v-if="error" class="error-banner">{{ error }}</div>

    <div class="workspace">
      <aside class="sidebar">
        <div class="sidebar-header">
          <button class="btn" :disabled="loading" @click="createGlobalVariable">新增全局变量</button>
        </div>
        <ul class="tree-list">
          <li
            v-for="item in sidebarItems"
            :key="itemKey(item)"
            class="tree-item"
            :class="{ active: activeKey === itemKey(item) }"
            @click="selectItem(item)"
          >
            <template v-if="item.kind === 'nav'">{{ item.label }}</template>
            <template v-else-if="item.kind === 'project'">
              <strong>项目</strong> {{ item.project.name }}
            </template>
            <template v-else-if="item.kind === 'group'">
              <span class="meta">组</span> {{ item.group.name }}
            </template>
            <template v-else-if="item.kind === 'workflow'">
              <span class="meta">工作流</span> {{ item.workflow.name }}
            </template>
            <template v-else-if="item.kind === 'step'">
              <span class="meta">{{ item.step.type }}</span> {{ item.step.name }}
            </template>
          </li>
        </ul>
      </aside>

      <main class="detail-panel">
        <template v-if="selection.type === 'global'">
          <h2 class="section-title">全局环境变量</h2>
          <table v-if="globalVariables.length" class="table">
            <thead>
              <tr>
                <th>变量名</th>
                <th>类型</th>
                <th>值</th>
                <th>启用</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in globalVariables" :key="item.id">
                <td>{{ item.key }}</td>
                <td>{{ item.valueType }}</td>
                <td>{{ JSON.stringify(item.value) }}</td>
                <td>{{ item.enabled ? '是' : '否' }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">暂无全局变量</div>
        </template>

        <template v-else-if="selection.type === 'history'">
          <h2 class="section-title">执行历史</h2>
          <table v-if="history.length" class="table">
            <thead>
              <tr>
                <th>类型</th>
                <th>目标</th>
                <th>状态</th>
                <th>耗时(ms)</th>
                <th>错误</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in history" :key="`${item.type}-${item.id}`">
                <td>{{ item.type }}</td>
                <td>{{ item.targetName }}</td>
                <td>{{ item.status }}</td>
                <td>{{ item.elapsedMs ?? '-' }}</td>
                <td>{{ item.errorMessage || '-' }}</td>
              </tr>
            </tbody>
          </table>
          <div v-else class="empty-state">暂无执行记录</div>
        </template>

        <template v-else-if="selection.type === 'project' && selection.project">
          <h2 class="section-title">项目详情</h2>
          <div class="field"><label>名称</label><input :value="selection.project.name" readonly /></div>
          <div class="field"><label>说明</label><textarea :value="selection.project.description || ''" readonly rows="3" /></div>
          <div class="actions">
            <button class="btn btn-primary" :disabled="loading" @click="createGroup">新建组</button>
            <button class="btn btn-primary" :disabled="loading" @click="runCurrent">执行项目</button>
            <button class="btn btn-danger" :disabled="loading" @click="deleteCurrent">删除项目</button>
          </div>
        </template>

        <template v-else-if="selection.type === 'group' && selection.group">
          <h2 class="section-title">组详情</h2>
          <div class="field"><label>名称</label><input :value="selection.group.name" readonly /></div>
          <div class="field"><label>说明</label><textarea :value="selection.group.description || ''" readonly rows="3" /></div>
          <div class="actions">
            <button class="btn btn-primary" :disabled="loading" @click="createWorkflow">新建工作流</button>
            <button class="btn btn-primary" :disabled="loading" @click="runCurrent">执行组</button>
            <button class="btn btn-danger" :disabled="loading" @click="deleteCurrent">删除组</button>
          </div>
        </template>

        <template v-else-if="selection.type === 'workflow' && selection.workflow">
          <h2 class="section-title">工作流详情</h2>
          <div class="field"><label>名称</label><input :value="selection.workflow.name" readonly /></div>
          <div class="field"><label>说明</label><textarea :value="selection.workflow.description || ''" readonly rows="3" /></div>
          <div class="actions">
            <button class="btn btn-primary" :disabled="loading" @click="createDelayStep">新增 DELAY 步骤</button>
            <button class="btn btn-primary" :disabled="loading" @click="createSetVarStep">新增 SET_VAR 步骤</button>
            <button class="btn btn-primary" :disabled="loading" @click="createDeleteVarStep">新增 DELETE_VAR 步骤</button>
            <button class="btn btn-primary" :disabled="loading" @click="runCurrent">执行工作流</button>
            <button class="btn btn-danger" :disabled="loading" @click="deleteCurrent">删除工作流</button>
          </div>
        </template>

        <template v-else-if="selection.type === 'step' && selection.step">
          <h2 class="section-title">步骤详情</h2>
          <div class="field"><label>编码</label><input :value="selection.step.code" readonly /></div>
          <div class="field"><label>名称</label><input :value="selection.step.name" readonly /></div>
          <div class="field"><label>类型</label><input :value="selection.step.type" readonly /></div>
          <div class="field"><label>配置 JSON</label><textarea :value="selection.step.configJson" readonly rows="8" /></div>
          <div class="actions">
            <button class="btn btn-primary" :disabled="loading" @click="runCurrent">执行所属工作流</button>
            <button class="btn btn-danger" :disabled="loading" @click="deleteCurrent">删除步骤</button>
          </div>
        </template>

        <div v-else class="empty-state">请选择左侧节点</div>
      </main>
    </div>
  </div>
</template>
