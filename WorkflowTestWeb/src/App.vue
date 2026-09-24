<script setup lang="ts">
import { onMounted } from 'vue'
import { provideAppState } from './composables/useAppState'
import CreateDialog from './components/common/CreateDialog.vue'
import GroupHookTargetDialog from './components/common/GroupHookTargetDialog.vue'
import AppStatusBar from './components/layout/AppStatusBar.vue'
import AppTopBar from './components/layout/AppTopBar.vue'
import PrimaryNav from './components/layout/PrimaryNav.vue'
import StepInspector from './components/inspector/StepInspector.vue'
import ProjectTree from './components/project/ProjectTree.vue'
import ExecutionHistoryPage from './components/pages/ExecutionHistoryPage.vue'
import GlobalVariablesPage from './components/pages/GlobalVariablesPage.vue'
import WorkspaceOverview from './components/pages/WorkspaceOverview.vue'
import WorkflowEditor from './components/workflow/WorkflowEditor.vue'

const app = provideAppState()

onMounted(() => {
  app.bootstrap().catch(() => undefined)
})
</script>

<template>
  <div class="app-shell">
    <AppTopBar />

    <div v-if="app.error.value" class="error-banner">{{ app.error.value }}</div>

    <div class="main-layout" :class="{ 'nav-collapsed': app.primaryNavCollapsed.value }">
      <PrimaryNav />

      <div
        class="content-area"
        :class="{
          'no-inspector': app.primaryNav.value !== 'projects',
          'hide-inspector': app.primaryNav.value === 'projects' && app.selection.value.kind === 'project',
          'tree-collapsed': app.primaryNav.value === 'projects' && app.projectTreeCollapsed.value
        }"
      >
        <ProjectTree v-if="app.primaryNav.value === 'projects'" />

        <main class="workspace-center">
          <GlobalVariablesPage v-if="app.primaryNav.value === 'globals'" />
          <ExecutionHistoryPage v-else-if="app.primaryNav.value === 'history'" />
          <WorkflowEditor
            v-else-if="app.selection.value.kind === 'workflow' || app.selection.value.kind === 'step'"
          />
          <WorkspaceOverview v-else />
        </main>

        <StepInspector
          v-if="app.primaryNav.value === 'projects' && app.selection.value.kind !== 'project'"
        />
      </div>
    </div>

    <AppStatusBar />
    <CreateDialog />
    <GroupHookTargetDialog />
  </div>
</template>
