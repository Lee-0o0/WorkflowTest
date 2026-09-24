<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { StepType } from '../../api/types'
import { useAppState } from '../../composables/useAppState'

const app = useAppState()
const nameInput = ref<HTMLInputElement | null>(null)

const dialog = computed(() => app.createDialog.value)

const showCode = computed(() => dialog.value.kind === 'step')
const showDescription = computed(() =>
  ['project', 'group', 'workflow'].includes(dialog.value.kind ?? '')
)
const showValue = computed(() => dialog.value.kind === 'globalVariable')

watch(
  () => dialog.value.visible,
  async (visible) => {
    if (!visible) return
    await nextTick()
    nameInput.value?.focus()
  }
)

function onBackdropClick(event: MouseEvent) {
  if (event.target === event.currentTarget) {
    app.closeCreateDialog()
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') app.closeCreateDialog()
}

function stepTypeLabel(type: StepType) {
  const map: Record<StepType, string> = {
    HTTP: 'HTTP 请求',
    SQL: 'SQL',
    DELAY: '延迟',
    SET_VAR: '赋值变量',
    DELETE_VAR: '删除变量'
  }
  return map[type]
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="dialog.visible"
      class="modal-backdrop"
      @click="onBackdropClick"
      @keydown="onKeydown"
    >
      <div class="modal-card" role="dialog" aria-modal="true">
        <div class="modal-header">
          <h3>{{ dialog.title }}</h3>
          <button class="icon-btn" type="button" aria-label="关闭" @click="app.closeCreateDialog()">×</button>
        </div>

        <form class="modal-body" @submit.prevent="app.submitCreateDialog()">
          <p v-if="dialog.subtitle" class="modal-subtitle">{{ dialog.subtitle }}</p>

          <label v-if="showCode" class="field">
            <span>步骤编码</span>
            <input v-model="dialog.code" required placeholder="例如 login_step" />
          </label>

          <label class="field">
            <span>{{ showValue ? '变量名' : '名称' }}</span>
            <input
              ref="nameInput"
              v-model="dialog.name"
              required
              :placeholder="showValue ? '例如 baseUrl' : '请输入名称'"
            />
          </label>

          <label v-if="showDescription" class="field">
            <span>说明</span>
            <textarea v-model="dialog.description" rows="3" placeholder="可选" />
          </label>

          <label v-if="showValue" class="field">
            <span>变量值</span>
            <textarea v-model="dialog.value" rows="4" placeholder='字符串或 JSON，例如 "http://localhost"' />
          </label>

          <label v-if="dialog.kind === 'step' && dialog.stepType" class="field">
            <span>步骤类型</span>
            <input :value="stepTypeLabel(dialog.stepType)" readonly />
          </label>

          <div class="modal-actions">
            <button type="button" class="btn" @click="app.closeCreateDialog()">取消</button>
            <button type="submit" class="btn btn-primary" :disabled="app.loading.value">确定</button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>
