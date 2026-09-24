<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import type { Group, Hook, Project, ProjectHook, Workflow } from '../../api/types'
import { useAppState } from '../../composables/useAppState'
import { hookTreeLabel } from '../../utils/treeDisplay'
import { PanelLeftClose, PanelRightOpen } from '@lucide/vue'
import ProjectTreeNode from './ProjectTreeNode.vue'

const app = useAppState()
const openMenuKey = ref<string | null>(null)
const hoveredKey = ref<string | null>(null)

function groups(projectId: number) {
  return app.groupsByProject.value[projectId] ?? []
}

function workflows(groupId: number) {
  return app.workflowsByGroup.value[groupId] ?? []
}

function projectHooks(projectId: number) {
  return app.projectHooksByProject.value[projectId] ?? []
}

function groupHooks(groupId: number) {
  return app.groupHooksByGroup.value[groupId] ?? []
}

function groupHooksBefore(groupId: number) {
  return groupHooks(groupId).filter((hook) => hook.hookType === 'BEFORE_GROUP')
}

function groupHooksAfter(groupId: number) {
  return groupHooks(groupId).filter((hook) => hook.hookType === 'AFTER_GROUP')
}

function toggleMenu(key: string) {
  openMenuKey.value = openMenuKey.value === key ? null : key
}

function closeMenu() {
  openMenuKey.value = null
}

function onHover(key: string) {
  hoveredKey.value = key
}

function onUnhover() {
  hoveredKey.value = null
}

function onDocumentClick() {
  closeMenu()
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onUnmounted(() => document.removeEventListener('click', onDocumentClick))

function isProjectActive(project: Project) {
  const s = app.selection.value
  return s.kind === 'project' && s.project?.id === project.id
}

function isGroupActive(group: Group) {
  const s = app.selection.value
  return s.kind === 'group' && s.group?.id === group.id
}

function isWorkflowActive(workflow: Workflow) {
  const s = app.selection.value
  return (s.kind === 'workflow' || s.kind === 'step') && s.workflow?.id === workflow.id
}

function projectHookLabel(hook: ProjectHook) {
  return hookTreeLabel(hook.hookType)
}

function groupHookLabel(hook: Hook) {
  return hookTreeLabel(hook.hookType)
}
</script>

<template>
  <aside class="project-tree-panel" :class="{ collapsed: app.projectTreeCollapsed.value }">
    <div v-if="app.projectTreeCollapsed.value" class="panel-collapsed-rail">
      <button
        class="rail-btn"
        title="展开项目树"
        @click="app.toggleProjectTreeCollapsed()"
      >
        <PanelRightOpen :size="18" :stroke-width="2" aria-hidden="true" />
      </button>
    </div>

    <template v-else>
      <div class="panel-header">
        <div class="panel-header-left">
          <button
            class="panel-edge-btn"
            title="收起项目树"
            @click.stop="app.toggleProjectTreeCollapsed()"
          >
            <PanelLeftClose :size="18" :stroke-width="2" aria-hidden="true" />
          </button>
          <strong>项目管理</strong>
        </div>
        <div class="panel-header-actions">
          <button class="icon-btn" title="新建项目" @click.stop="app.createProject()">+</button>
        </div>
      </div>

    <div v-if="!app.projects.value.length" class="empty-state">暂无项目，点击 + 创建</div>

    <ul v-else class="tree-root">
      <li v-for="project in app.projects.value" :key="project.id">
        <ProjectTreeNode
          :node-key="`project-${project.id}`"
          :active="isProjectActive(project)"
          :hovered-key="hoveredKey"
          :open-menu-key="openMenuKey"
          level="project"
          :label="project.name"
          :expanded="app.expandedProjects.has(project.id)"
          :expandable="true"
          @hover="onHover"
          @unhover="onUnhover"
          @toggle-menu="toggleMenu"
          @close-menu="closeMenu"
          @toggle="app.toggleProject(project.id)"
          @select="app.selectProject(project)"
          @run="app.runProject(project)"
          @add-group="app.createGroup(project)"
          @add-hook="app.createProjectHook(project)"
          @delete="app.selectProject(project); app.deleteSelection()"
        />
        <ul v-if="app.expandedProjects.has(project.id)" class="tree-children">
          <li v-for="hook in projectHooks(project.id)" :key="`ph-${hook.id}`">
            <ProjectTreeNode
              :node-key="`project-hook-${hook.id}`"
              :active="false"
              :hovered-key="hoveredKey"
              :open-menu-key="openMenuKey"
              level="hook"
              :label="projectHookLabel(hook)"
              :expanded="false"
              :show-toggle="false"
              @hover="onHover"
              @unhover="onUnhover"
              @toggle-menu="toggleMenu"
              @close-menu="closeMenu"
              @select="app.selectProject(project)"
            />
          </li>
          <li v-for="group in groups(project.id)" :key="group.id">
            <ProjectTreeNode
              :node-key="`group-${group.id}`"
              :active="isGroupActive(group)"
              :hovered-key="hoveredKey"
              :open-menu-key="openMenuKey"
              level="group"
              :label="group.name"
              :expanded="app.expandedGroups.has(group.id)"
              :expandable="true"
              @hover="onHover"
              @unhover="onUnhover"
              @toggle-menu="toggleMenu"
              @close-menu="closeMenu"
              @toggle="app.toggleGroup(group.id)"
              @select="app.selectGroup(project, group)"
              @run="app.runGroup(group)"
              @add-workflow="app.createWorkflow(project, group)"
              @add-hook="app.createGroupHook(group, 'BEFORE_GROUP')"
              @delete="app.selectGroup(project, group); app.deleteSelection()"
            />
            <ul v-if="app.expandedGroups.has(group.id)" class="tree-children">
              <li v-for="hook in groupHooksBefore(group.id)" :key="`gh-before-${hook.id}`">
                <ProjectTreeNode
                  :node-key="`group-hook-${hook.id}`"
                  :active="false"
                  :hovered-key="hoveredKey"
                  :open-menu-key="openMenuKey"
                  level="hook"
                  :label="groupHookLabel(hook)"
                  :expanded="false"
                  :show-toggle="false"
                  @hover="onHover"
                  @unhover="onUnhover"
                  @toggle-menu="toggleMenu"
                  @close-menu="closeMenu"
                  @select="app.selectGroup(project, group)"
                />
              </li>
              <li v-for="workflow in workflows(group.id)" :key="workflow.id">
                <ProjectTreeNode
                  :node-key="`workflow-${workflow.id}`"
                  :active="isWorkflowActive(workflow)"
                  :hovered-key="hoveredKey"
                  :open-menu-key="openMenuKey"
                  level="workflow"
                  :label="workflow.name"
                  :expanded="false"
                  :show-toggle="false"
                  @hover="onHover"
                  @unhover="onUnhover"
                  @toggle-menu="toggleMenu"
                  @close-menu="closeMenu"
                  @select="app.selectWorkflow(project, group, workflow)"
                  @run="app.runWorkflow(workflow)"
                  @delete="app.selectWorkflow(project, group, workflow); app.deleteSelection()"
                />
              </li>
              <li v-for="hook in groupHooksAfter(group.id)" :key="`gh-after-${hook.id}`">
                <ProjectTreeNode
                  :node-key="`group-hook-${hook.id}`"
                  :active="false"
                  :hovered-key="hoveredKey"
                  :open-menu-key="openMenuKey"
                  level="hook"
                  :label="groupHookLabel(hook)"
                  :expanded="false"
                  :show-toggle="false"
                  @hover="onHover"
                  @unhover="onUnhover"
                  @toggle-menu="toggleMenu"
                  @close-menu="closeMenu"
                  @select="app.selectGroup(project, group)"
                />
              </li>
            </ul>
          </li>
        </ul>
      </li>
    </ul>
    </template>
  </aside>
</template>
