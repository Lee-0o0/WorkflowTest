<script setup lang="ts">
import { formatDuration, statusIcon } from '../../utils/stepDisplay'
import { useAppState } from '../../composables/useAppState'

const app = useAppState()

function formatTime(value?: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}
</script>

<template>
  <section class="page-panel history-page">
    <div class="page-header">
      <h2>执行历史</h2>
      <button class="btn" :disabled="app.loading.value" @click="app.refreshHistory()">刷新</button>
    </div>

    <div class="history-layout">
      <div class="history-list">
        <table v-if="app.history.value.length" class="table">
          <thead>
            <tr>
              <th>执行时间</th>
              <th>类型</th>
              <th>名称</th>
              <th>状态</th>
              <th>耗时</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in app.history.value"
              :key="item.id"
              class="clickable"
              :class="{ active: app.historyDetail.value?.id === item.id }"
              @click="app.openHistoryDetail(item)"
            >
              <td>{{ formatTime(item.startedAt) }}</td>
              <td>{{ item.type }}</td>
              <td>{{ item.targetName }}</td>
              <td><span :class="'status-' + item.status.toLowerCase()">{{ statusIcon(item.status) }} {{ item.status }}</span></td>
              <td>{{ formatDuration(item.elapsedMs) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty-state">暂无执行记录</div>
      </div>

      <div v-if="app.historyDetail.value" class="history-detail">
        <h3>{{ app.historyDetail.value.targetName }} · 执行详情</h3>
        <div class="detail-meta">
          <span>状态：{{ app.historyDetail.value.status }}</span>
          <span>开始：{{ formatTime(app.historyDetail.value.startedAt) }}</span>
          <span>耗时：{{ formatDuration(app.historyDetail.value.elapsedMs) }}</span>
        </div>
        <p v-if="app.historyDetail.value.errorMessage" class="error-text">{{ app.historyDetail.value.errorMessage }}</p>

        <div class="history-detail-grid">
          <div class="history-steps-list">
            <h4>步骤</h4>
            <button
              v-for="step in app.historySteps.value"
              :key="step.id"
              class="history-step-item"
              :class="{ active: app.selectedHistoryStep.value?.id === step.id }"
              @click="app.selectedHistoryStep.value = step"
            >
              {{ statusIcon(step.status) }} {{ step.stepCode }} · {{ step.phase }}
            </button>
          </div>
          <div class="history-step-detail">
            <template v-if="app.selectedHistoryStep.value">
              <h4>{{ app.selectedHistoryStep.value.stepCode }}</h4>
              <pre v-if="app.selectedHistoryStep.value.outputJson">{{ app.selectedHistoryStep.value.outputJson }}</pre>
              <pre v-else-if="app.selectedHistoryStep.value.errorMessage">{{ app.selectedHistoryStep.value.errorMessage }}</pre>
              <div v-else class="muted">选择左侧步骤查看输出</div>
            </template>
            <div v-else class="muted">点击步骤查看 Request / Response / Assertion 详情</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
