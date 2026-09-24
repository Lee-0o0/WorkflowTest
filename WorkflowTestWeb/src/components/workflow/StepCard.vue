<script setup lang="ts">
import type { Step } from '../../api/types'
import { toStepCardView } from '../../utils/stepDisplay'

defineProps<{
  step: Step
  index: number
  active: boolean
}>()

defineEmits<{ select: [] }>()
</script>

<template>
  <div class="step-card" :class="{ active }" @click="$emit('select')">
    <div class="step-card-head">
      <span class="step-index">{{ String(index).padStart(2, '0') }}</span>
      <span class="step-badge">{{ toStepCardView(step, index).badge }}</span>
      <span class="step-status">○</span>
    </div>
    <div class="step-title">{{ step.name }}</div>
    <div class="step-subtitle">{{ toStepCardView(step, index).subtitle }}</div>
  </div>
</template>
