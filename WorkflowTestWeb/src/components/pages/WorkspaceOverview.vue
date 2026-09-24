<script setup lang="ts">
import { computed } from 'vue'
import { useAppState } from '../../composables/useAppState'
import TreeTypeIcon from '../common/TreeTypeIcon.vue'

const app = useAppState()
const selection = computed(() => app.selection.value)
</script>

<template>
  <section class="page-panel overview-panel">
    <template v-if="selection.kind === 'project' && selection.project">
      <h2 class="title-with-icon">
        <TreeTypeIcon kind="project" :size="22" />
        {{ selection.project.name }}
      </h2>
      <p class="muted">{{ selection.project.description || '项目概览' }}</p>
      <div class="overview-actions">
        <button class="btn btn-primary" @click="app.createGroup(selection.project!)">新建组</button>
        <button class="btn btn-primary" @click="app.runProject(selection.project!)">▶ 执行项目</button>
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
