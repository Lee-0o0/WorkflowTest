<script setup lang="ts">
import { computed } from 'vue'
import type { StepType } from '../../api/types'
import { useAppState } from '../../composables/useAppState'
import TreeTypeIcon from '../common/TreeTypeIcon.vue'
import HookSection from './HookSection.vue'
import StepCard from './StepCard.vue'

const app = useAppState()

const selection = computed(() => app.selection.value)
const workflow = computed(() => selection.value.workflow)
const steps = computed(() => {
  if (!workflow.value) return []
  return (app.stepsByWorkflow.value[workflow.value.id] ?? []).slice().sort((a, b) => a.sortOrder - b.sortOrder)
})

const hookBlocks = computed(() => {
  const s = selection.value
  if (!s.project || !s.group) return []
  const blocks: Array<{ label: string; steps: import('../../api/types').Step[] }> = []

  const projectHooks = app.projectHooksByProject.value[s.project.id] ?? []
  for (const hook of projectHooks) {
    blocks.push({ label: 'Project · Before Each Group', steps: hook.steps ?? [] })
  }

  const groupHooks = app.groupHooksByGroup.value[s.group.id] ?? []
  for (const hook of groupHooks) {
    const label = hook.hookType === 'BEFORE_GROUP' ? 'Group · Before' : 'Group · After'
    blocks.push({ label, steps: hook.steps ?? [] })
  }
  return blocks
})

const stepTypes: StepType[] = ['HTTP', 'SQL', 'DELAY', 'SET_VAR', 'DELETE_VAR']

function addStep(type: StepType) {
  const s = selection.value
  if (!s.project || !s.group || !s.workflow) return
  app.createWorkflowStep(s.workflow, type, s.project, s.group)
}
</script>

<template>
  <div v-if="workflow" class="workflow-editor">
    <div class="editor-header">
      <div>
        <h2 class="title-with-icon">
          <TreeTypeIcon kind="workflow" :size="22" />
          {{ workflow.name }}
        </h2>
        <p class="muted">工作流步骤编排 · 执行结果请查看「执行历史」</p>
      </div>
      <button class="btn btn-primary" :disabled="app.loading.value" @click="app.runWorkflow(workflow)">▶ 执行</button>
    </div>

    <HookSection title="生命周期" :hooks="hookBlocks" />

    <section class="steps-section">
      <h3 class="section-label">Steps</h3>
      <div class="step-flow">
        <template v-for="(step, idx) in steps" :key="step.id">
          <StepCard
            :step="step"
            :index="idx + 1"
            :active="selection.kind === 'step' && selection.step?.id === step.id"
            @select="app.selectStep(selection.project!, selection.group!, workflow, step)"
          />
          <div v-if="idx < steps.length - 1" class="step-arrow">↓</div>
        </template>
      </div>

      <div class="add-step-bar">
        <span>+ 添加步骤</span>
        <div class="step-type-buttons">
          <button v-for="type in stepTypes" :key="type" class="btn" @click="addStep(type)">
            {{ type }}
          </button>
        </div>
      </div>
    </section>
  </div>
</template>
