<script setup lang="ts">
import { computed } from 'vue'
import type { Group } from '../../api/types'
import { useAppState } from '../../composables/useAppState'

const app = useAppState()

const dialog = computed(() => app.groupHookTargetDialog.value)
const groups = computed(() => dialog.value.groups ?? [])

function hookTypeLabel(hookType?: 'BEFORE_GROUP' | 'AFTER_GROUP') {
  return hookType === 'BEFORE_GROUP' ? '组前钩子' : '组后钩子'
}

function onBackdropClick(event: MouseEvent) {
  if (event.target === event.currentTarget) {
    app.closeGroupHookTargetDialog()
  }
}

function onSubmit() {
  app.confirmGroupHookTargetDialog()
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="dialog.visible"
      class="modal-backdrop"
      @click="onBackdropClick"
    >
      <div class="modal-card" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h3>选择目标组</h3>
          <button class="icon-btn" type="button" aria-label="关闭" @click="app.closeGroupHookTargetDialog()">×</button>
        </div>

        <form class="modal-body" @submit.prevent="onSubmit">
          <p class="modal-subtitle">
            为哪个组创建{{ hookTypeLabel(dialog.hookType) }}？
          </p>

          <label class="field">
            <span>目标组</span>
            <select v-model="app.groupHookTargetDialog.groupId" required>
              <option v-for="group in groups" :key="group.id" :value="group.id">
                {{ group.name }}
              </option>
            </select>
          </label>

          <div class="modal-actions">
            <button type="button" class="btn" @click="app.closeGroupHookTargetDialog()">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="app.loading.value || !app.groupHookTargetDialog.groupId">
              确定
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>
