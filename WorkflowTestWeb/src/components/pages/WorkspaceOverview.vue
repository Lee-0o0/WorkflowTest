<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useAppState } from '../../composables/useAppState'
import CreateDropdown from '../common/CreateDropdown.vue'
import TreeTypeIcon from '../common/TreeTypeIcon.vue'

const app = useAppState()
const selection = computed(() => app.selection.value)

const editingName = ref(false)
const draftName = ref('')
const draftDescription = ref('')
const nameInputRef = ref<HTMLInputElement | null>(null)

watch(
  () => [selection.value.kind, selection.value.project?.id, selection.value.project?.name, selection.value.project?.description] as const,
  () => {
    const project = selection.value.project
    if (selection.value.kind !== 'project' || !project) return
    draftName.value = project.name
    draftDescription.value = project.description ?? ''
    editingName.value = false
  },
  { immediate: true }
)

const canSave = computed(() => draftName.value.trim().length > 0)

const projectGroups = computed(() => {
  const project = selection.value.project
  if (!project) return []
  return app.groupsByProject.value[project.id] ?? []
})

const createMenuItems = computed(() => [
  { key: 'group', label: '新建组' },
  {
    key: 'hook-before',
    label: '新建组前钩子',
    disabled: projectGroups.value.length === 0
  },
  {
    key: 'hook-after',
    label: '新建组后钩子',
    disabled: projectGroups.value.length === 0
  }
])

function startEditName() {
  editingName.value = true
  nextTick(() => {
    nameInputRef.value?.focus()
    nameInputRef.value?.select()
  })
}

function finishEditName() {
  editingName.value = false
  draftName.value = draftName.value.trim() || selection.value.project?.name || ''
}

async function saveProject() {
  const project = selection.value.project
  if (!project || !canSave.value) return
  await app.saveProject(project, draftName.value, draftDescription.value)
}

function onNameKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter') {
    event.preventDefault()
    finishEditName()
  }
  if (event.key === 'Escape') {
    draftName.value = selection.value.project?.name ?? ''
    editingName.value = false
  }
}

function onCreateSelect(key: string) {
  const project = selection.value.project
  if (!project) return
  if (key === 'group') {
    app.createGroup(project)
    return
  }
  if (key === 'hook-before') {
    app.createProjectGroupHook(project, 'BEFORE_GROUP')
    return
  }
  if (key === 'hook-after') {
    app.createProjectGroupHook(project, 'AFTER_GROUP')
  }
}
</script>

<template>
  <section class="page-panel overview-panel">
    <template v-if="selection.kind === 'project' && selection.project">
      <div class="entity-editor project-editor">
        <div class="entity-editor-header">
          <TreeTypeIcon kind="project" :size="22" />
          <div class="entity-title-wrap">
            <input
              v-if="editingName"
              ref="nameInputRef"
              v-model="draftName"
              class="entity-name-input"
              @blur="finishEditName"
              @keydown="onNameKeydown"
            />
            <h2 v-else class="entity-name" title="双击编辑项目名称" @dblclick="startEditName">
              {{ draftName }}
            </h2>
          </div>
          <div class="entity-header-actions">
            <CreateDropdown label="新建" :items="createMenuItems" @select="onCreateSelect" />
            <button class="btn btn-primary" @click="app.runProject(selection.project!)">▶ 执行项目</button>
          </div>
        </div>

        <label class="field">
          <span>项目介绍</span>
          <textarea
            v-model="draftDescription"
            rows="8"
            placeholder="输入项目介绍、背景说明或使用备注…"
          />
        </label>

        <div class="overview-actions overview-actions-right">
          <button class="btn btn-primary" :disabled="app.loading.value || !canSave" @click="saveProject">
            保存
          </button>
        </div>
      </div>
    </template>

    <template v-else-if="selection.kind === 'group' && selection.group">
      <h2 class="title-with-icon">
        <TreeTypeIcon kind="group" :size="22" />
        {{ selection.group.name }}
      </h2>
      <p class="muted">{{ selection.group.description || '组概览' }}</p>
      <div class="overview-actions">
        <button class="btn btn-primary" @click="app.createWorkflow(selection.project!, selection.group!)">新建工作流</button>
        <button class="btn btn-primary" @click="app.runGroup(selection.group!)">▶ 执行组</button>
      </div>
    </template>

    <template v-else>
      <h2>TestFlow</h2>
      <p class="muted">左侧选择项目、组或工作流开始编排测试流程</p>
      <button class="btn btn-primary" @click="app.createProject()">新建项目</button>
    </template>
  </section>
</template>
