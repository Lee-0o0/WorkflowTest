<script setup lang="ts">
import { computed } from 'vue'
import { ChevronDown, ChevronRight } from '@lucide/vue'
import type { TreeNodeKind } from '../../utils/treeDisplay'
import TreeTypeIcon from '../common/TreeTypeIcon.vue'

const props = defineProps<{
  level: TreeNodeKind
  label: string
  expanded: boolean
  expandable?: boolean
  showToggle?: boolean
  nodeKey: string
  active: boolean
  hoveredKey: string | null
  openMenuKey: string | null
}>()

const emit = defineEmits<{
  toggle: []
  select: []
  run: []
  'add-group': []
  'add-workflow': []
  'add-hook': []
  delete: []
  'toggle-menu': [key: string]
  'close-menu': []
  hover: [key: string]
  unhover: []
}>()

const menuOpen = computed(() => props.openMenuKey === props.nodeKey)

const canExpand = computed(() => {
  if (props.expandable != null) return props.expandable
  return props.showToggle !== false && (props.level === 'project' || props.level === 'group')
})

/** 同一时刻仅一个节点显示操作区：优先 hover，否则仅精确选中的节点 */
const showActions = computed(() => {
  if (props.hoveredKey !== null) {
    return props.hoveredKey === props.nodeKey
  }
  return props.active
})

function onMouseEnter() {
  emit('hover', props.nodeKey)
}

function onMouseLeave() {
  emit('unhover')
  if (menuOpen.value) {
    emit('close-menu')
  }
}

function toggleMenu(event: MouseEvent) {
  event.stopPropagation()
  emit('toggle-menu', props.nodeKey)
}

function onMenu(action: () => void) {
  action()
  emit('close-menu')
}
</script>

<template>
  <div
    class="tree-node"
    :class="{ active: showActions, 'show-actions': showActions }"
    @mouseenter="onMouseEnter"
    @mouseleave="onMouseLeave"
    @click.stop="emit('select')"
  >
    <span class="tree-gutter">
      <button
        v-if="canExpand"
        class="tree-toggle-btn"
        :title="expanded ? '收起' : '展开'"
        @click.stop="emit('toggle')"
      >
        <ChevronDown v-if="expanded" :size="18" :stroke-width="2.5" aria-hidden="true" />
        <ChevronRight v-else :size="18" :stroke-width="2.5" aria-hidden="true" />
      </button>
    </span>

    <TreeTypeIcon :kind="level" />
    <span class="node-label">{{ label }}</span>

    <div v-if="level !== 'hook'" class="node-actions" @click.stop>
      <button v-if="level === 'project'" class="mini-btn" title="新建组" @click="emit('add-group')">+</button>
      <button v-if="level === 'group'" class="mini-btn" title="新建工作流" @click="emit('add-workflow')">+</button>

      <div class="menu-wrap">
        <button class="mini-btn" title="更多" @click="toggleMenu">⋮</button>
        <div v-if="menuOpen" class="context-menu" @click.stop>
          <template v-if="level === 'project'">
            <button @click="onMenu(() => emit('add-group'))">新建组</button>
            <button @click="onMenu(() => emit('add-hook'))">新建钩子</button>
            <button @click="onMenu(() => emit('run'))">执行项目</button>
            <button @click="onMenu(() => emit('delete'))">删除</button>
          </template>
          <template v-else-if="level === 'group'">
            <button @click="onMenu(() => emit('add-workflow'))">新建工作流</button>
            <button @click="onMenu(() => emit('add-hook'))">新建钩子</button>
            <button @click="onMenu(() => emit('run'))">执行组</button>
            <button @click="onMenu(() => emit('delete'))">删除</button>
          </template>
          <template v-else>
            <button @click="onMenu(() => emit('run'))">执行工作流</button>
            <button @click="onMenu(() => emit('delete'))">删除</button>
          </template>
        </div>
      </div>

      <button class="mini-btn run-btn" title="执行" @click="emit('run')">▶</button>
    </div>
  </div>
</template>
