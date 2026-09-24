<script setup lang="ts">
import type { Step } from '../../api/types'
import { stepTypeLabel } from '../../utils/stepDisplay'
import TreeTypeIcon from '../common/TreeTypeIcon.vue'

defineProps<{
  title: string
  hooks: Array<{ label: string; steps: Step[] }>
}>()
</script>

<template>
  <section class="hook-section">
    <h3 class="section-label title-with-icon">
      <TreeTypeIcon kind="hook" :size="18" />
      Hooks · {{ title }}
    </h3>
    <div v-if="!hooks.length" class="hook-empty">暂无钩子，可通过项目/组节点 ⋮ 菜单创建</div>
    <div v-for="block in hooks" :key="block.label" class="hook-block">
      <div class="hook-block-title title-with-icon">
        <TreeTypeIcon kind="hook" :size="16" />
        {{ block.label }}
      </div>
      <ul class="hook-step-list">
        <li v-for="step in block.steps" :key="step.id">
          <span class="hook-step-name">{{ step.name }}</span>
          <span class="hook-step-type">{{ stepTypeLabel(step.type) }}</span>
        </li>
        <li v-if="!block.steps.length" class="hook-empty-inline">（空）</li>
      </ul>
    </div>
  </section>
</template>
