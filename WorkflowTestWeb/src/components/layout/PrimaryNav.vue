<script setup lang="ts">
import type { PrimaryNav } from '../../api/types'
import { useAppState } from '../../composables/useAppState'
import { FolderKanban, Globe, History, PanelLeftClose, PanelLeftOpen } from '@lucide/vue'

const app = useAppState()

const items: Array<{ key: PrimaryNav; label: string; icon: typeof FolderKanban }> = [
  { key: 'projects', label: '项目管理', icon: FolderKanban },
  { key: 'globals', label: '全局变量', icon: Globe },
  { key: 'history', label: '执行历史', icon: History }
]

function selectNav(key: PrimaryNav) {
  app.primaryNav.value = key
  if (key === 'globals') app.refreshGlobalVariables().catch(() => undefined)
  if (key === 'history') app.refreshHistory().catch(() => undefined)
}
</script>

<template>
  <nav class="primary-nav" :class="{ collapsed: app.primaryNavCollapsed.value }">
    <button
      v-for="item in items"
      :key="item.key"
      class="nav-item"
      :class="{ active: app.primaryNav.value === item.key }"
      :title="item.label"
      @click="selectNav(item.key)"
    >
      <span class="nav-icon">
        <component :is="item.icon" :size="20" :stroke-width="2" aria-hidden="true" />
      </span>
      <span class="nav-label">{{ item.label }}</span>
    </button>

    <button
      class="nav-collapse-btn"
      :title="app.primaryNavCollapsed.value ? '展开导航' : '收起导航'"
      @click="app.togglePrimaryNavCollapsed()"
    >
      <PanelLeftOpen v-if="app.primaryNavCollapsed.value" :size="18" :stroke-width="2" aria-hidden="true" />
      <PanelLeftClose v-else :size="18" :stroke-width="2" aria-hidden="true" />
    </button>
  </nav>
</template>
