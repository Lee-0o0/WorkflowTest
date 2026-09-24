<script setup lang="ts">
import { useAppState } from '../../composables/useAppState'

const app = useAppState()
</script>

<template>
  <header class="topbar">
    <div class="topbar-left">
      <div class="brand">TestFlow</div>
      <div class="context">
        <span v-if="app.currentProject.value">项目：{{ app.currentProject.value.name }}</span>
        <span v-if="app.currentWorkflow.value" class="divider">/</span>
        <span v-if="app.currentWorkflow.value">{{ app.currentWorkflow.value.name }}</span>
      </div>
    </div>
  </header>
</template>
