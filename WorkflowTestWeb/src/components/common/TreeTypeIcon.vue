<script setup lang="ts">
import { computed } from 'vue'
import { FolderKanban, Layers, Webhook, Workflow } from '@lucide/vue'
import { treeNodeIconClass, treeNodeTitle, type TreeNodeKind } from '../../utils/treeDisplay'

const props = withDefaults(
  defineProps<{
    kind: TreeNodeKind
    size?: number
    strokeWidth?: number
  }>(),
  {
    size: 18,
    strokeWidth: 2.25
  }
)

const ICONS = {
  project: FolderKanban,
  group: Layers,
  workflow: Workflow,
  hook: Webhook
} as const

const Icon = computed(() => ICONS[props.kind])
const iconClass = computed(() => treeNodeIconClass(props.kind))
const title = computed(() => treeNodeTitle(props.kind))
</script>

<template>
  <span
    class="node-type-icon"
    :class="iconClass"
    :title="title"
    :style="{ width: `${size}px`, height: `${size}px` }"
  >
    <component :is="Icon" :size="size" :stroke-width="strokeWidth" aria-hidden="true" />
  </span>
</template>
