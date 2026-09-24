<script setup lang="ts">
import { computed } from 'vue'
import { useAppState } from '../../composables/useAppState'
import { parseConfig, stepTypeLabel } from '../../utils/stepDisplay'
import TreeTypeIcon from '../common/TreeTypeIcon.vue'

const app = useAppState()

const step = computed(() => app.selection.value.step)

const config = computed(() => (step.value ? parseConfig(step.value) : {}))

function updateField(field: 'name' | 'code' | 'configJson' | 'extractionJson' | 'assertionJson', value: string) {
  if (!step.value) return
  const next = { ...step.value, [field]: value }
  app.selection.value = { ...app.selection.value, step: next }
}

function updateConfigField(key: string, value: string | number) {
  if (!step.value) return
  const nextConfig = { ...config.value, [key]: value }
  updateField('configJson', JSON.stringify(nextConfig, null, 2))
}
</script>

<template>
  <aside v-if="step" class="inspector">
    <div class="inspector-header">
      <h3>{{ stepTypeLabel(step.type) }}</h3>
      <span class="muted">Configuration Inspector</span>
    </div>

    <div class="inspector-body">
      <section class="inspector-section">
        <h4>基本信息</h4>
        <label class="field">
          <span>名称</span>
          <input :value="step.name" @input="updateField('name', ($event.target as HTMLInputElement).value)" />
        </label>
        <label class="field">
          <span>编码</span>
          <input :value="step.code" @input="updateField('code', ($event.target as HTMLInputElement).value)" />
        </label>
      </section>

      <section v-if="step.type === 'HTTP'" class="inspector-section">
        <h4>HTTP 请求</h4>
        <label class="field">
          <span>方法</span>
          <select :value="String(config.method ?? 'GET')" @change="updateConfigField('method', ($event.target as HTMLSelectElement).value)">
            <option>GET</option>
            <option>POST</option>
            <option>PUT</option>
            <option>PATCH</option>
            <option>DELETE</option>
          </select>
        </label>
        <label class="field">
          <span>URL</span>
          <input :value="String(config.url ?? '')" @input="updateConfigField('url', ($event.target as HTMLInputElement).value)" />
        </label>
        <label class="field">
          <span>Body (JSON)</span>
          <textarea
            rows="8"
            :value="typeof config.body === 'object' ? JSON.stringify(config.body, null, 2) : String(config.body ?? '')"
            @input="updateConfigField('body', ($event.target as HTMLTextAreaElement).value)"
          />
        </label>
        <label class="field">
          <span>连接超时 (ms)</span>
          <input
            type="number"
            :value="Number(config.connectTimeoutMs ?? 5000)"
            @input="updateConfigField('connectTimeoutMs', Number(($event.target as HTMLInputElement).value))"
          />
        </label>
        <label class="field">
          <span>读取超时 (ms)</span>
          <input
            type="number"
            :value="Number(config.readTimeoutMs ?? 10000)"
            @input="updateConfigField('readTimeoutMs', Number(($event.target as HTMLInputElement).value))"
          />
        </label>
      </section>

      <section v-else-if="step.type === 'SQL'" class="inspector-section">
        <h4>SQL</h4>
        <label class="field">
          <span>数据源 ID</span>
          <input
            type="number"
            :value="Number(config.datasourceId ?? 1)"
            @input="updateConfigField('datasourceId', Number(($event.target as HTMLInputElement).value))"
          />
        </label>
        <label class="field">
          <span>操作</span>
          <select :value="String(config.operation ?? 'QUERY')" @change="updateConfigField('operation', ($event.target as HTMLSelectElement).value)">
            <option>QUERY</option>
            <option>UPDATE</option>
          </select>
        </label>
        <label class="field">
          <span>SQL</span>
          <textarea rows="8" :value="String(config.sql ?? '')" @input="updateConfigField('sql', ($event.target as HTMLTextAreaElement).value)" />
        </label>
      </section>

      <section v-else class="inspector-section">
        <h4>配置 JSON</h4>
        <textarea
          rows="12"
          :value="step.configJson"
          @input="updateField('configJson', ($event.target as HTMLTextAreaElement).value)"
        />
      </section>

      <section class="inspector-section">
        <h4>提取规则 JSON</h4>
        <textarea
          rows="5"
          :value="step.extractionJson ?? '[]'"
          @input="updateField('extractionJson', ($event.target as HTMLTextAreaElement).value)"
        />
      </section>

      <section class="inspector-section">
        <h4>断言规则 JSON</h4>
        <textarea
          rows="5"
          :value="step.assertionJson ?? '[]'"
          @input="updateField('assertionJson', ($event.target as HTMLTextAreaElement).value)"
        />
      </section>

      <div class="inspector-actions">
        <button class="btn btn-primary" :disabled="app.loading.value" @click="app.saveCurrentStep()">保存步骤</button>
        <button class="btn btn-danger" :disabled="app.loading.value" @click="app.deleteSelection()">删除</button>
      </div>
    </div>
  </aside>

  <aside v-else-if="app.selection.value.kind !== 'none'" class="inspector">
    <div class="inspector-header">
      <h3>属性</h3>
      <span class="muted">选择步骤以编辑详细配置</span>
    </div>
    <div class="inspector-body empty-inspector">
      <p v-if="app.selection.value.project" class="title-with-icon">
        <TreeTypeIcon kind="project" :size="16" />
        项目：{{ app.selection.value.project.name }}
      </p>
      <p v-if="app.selection.value.group" class="title-with-icon">
        <TreeTypeIcon kind="group" :size="16" />
        组：{{ app.selection.value.group.name }}
      </p>
      <p v-if="app.selection.value.workflow" class="title-with-icon">
        <TreeTypeIcon kind="workflow" :size="16" />
        工作流：{{ app.selection.value.workflow.name }}
      </p>
      <p class="muted">点击步骤卡片查看 HTTP / SQL 等配置面板</p>
    </div>
  </aside>
</template>
