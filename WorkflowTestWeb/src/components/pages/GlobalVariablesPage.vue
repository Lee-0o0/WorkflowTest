<script setup lang="ts">
import { api } from '../../api/client'
import { useAppState } from '../../composables/useAppState'

const app = useAppState()

async function remove(id: number) {
  if (!window.confirm('删除该变量？')) return
  await app.withLoading(async () => {
    await api.deleteGlobalVariable(id)
    await app.refreshGlobalVariables()
  })
}
</script>

<template>
  <section class="page-panel">
    <div class="page-header">
      <h2>全局变量</h2>
      <button class="btn btn-primary" :disabled="app.loading.value" @click="app.openCreateGlobalVariableDialog()">
        + 新增变量
      </button>
    </div>

    <table v-if="app.globalVariables.value.length" class="table">
      <thead>
        <tr>
          <th>变量名</th>
          <th>值</th>
          <th>类型</th>
          <th>启用</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in app.globalVariables.value" :key="item.id">
          <td><code>{{ item.key }}</code></td>
          <td>{{ JSON.stringify(item.value) }}</td>
          <td>{{ item.valueType }}</td>
          <td>{{ item.enabled ? '是' : '否' }}</td>
          <td><button class="btn btn-danger" @click="remove(item.id)">删除</button></td>
        </tr>
      </tbody>
    </table>
    <div v-else class="empty-state">暂无全局变量</div>
  </section>
</template>
