<script setup lang="ts">
import { useAppState } from '../../composables/useAppState'

const app = useAppState()
</script>

<template>
  <footer class="statusbar">
    <span>{{ app.statusText.value }}</span>
    <span class="muted">{{ app.lastSavedHint.value }}</span>
    <span class="muted">{{ app.serverStatus.value }}</span>
  </footer>
</template>
