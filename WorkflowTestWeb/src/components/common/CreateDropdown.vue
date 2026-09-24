<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { ChevronDown } from '@lucide/vue'

export interface CreateDropdownItem {
  key: string
  label: string
  disabled?: boolean
}

const props = withDefaults(
  defineProps<{
    label?: string
    items: CreateDropdownItem[]
    disabled?: boolean
  }>(),
  {
    label: '新建',
    disabled: false
  }
)

const emit = defineEmits<{ select: [key: string] }>()

const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)

function toggle() {
  if (props.disabled) return
  open.value = !open.value
}

function close() {
  open.value = false
}

function onSelect(item: CreateDropdownItem) {
  if (item.disabled) return
  emit('select', item.key)
  close()
}

function onDocumentClick(event: MouseEvent) {
  if (!rootRef.value?.contains(event.target as Node)) {
    close()
  }
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onUnmounted(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <div ref="rootRef" class="split-dropdown">
    <button type="button" class="btn split-dropdown-trigger" :disabled="disabled" @click.stop="toggle">
      <span>{{ label }}</span>
      <ChevronDown :size="16" :stroke-width="2.25" :class="{ open }" aria-hidden="true" />
    </button>
    <div v-if="open" class="split-dropdown-menu" @click.stop>
      <button
        v-for="item in items"
        :key="item.key"
        type="button"
        class="split-dropdown-item"
        :disabled="item.disabled"
        @click="onSelect(item)"
      >
        {{ item.label }}
      </button>
    </div>
  </div>
</template>
