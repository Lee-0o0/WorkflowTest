import type { Step, StepType } from '../api/types'

export interface StepCardView {
  index: number
  title: string
  subtitle: string
  badge: string
  hasExtraction: boolean
  hasAssertion: boolean
}

export function stepTypeLabel(type: StepType): string {
  const map: Record<StepType, string> = {
    HTTP: 'HTTP 请求',
    SQL: 'SQL 查询',
    DELAY: '延迟',
    SET_VAR: '赋值变量',
    DELETE_VAR: '删除变量'
  }
  return map[type] ?? type
}

export function parseConfig(step: Step): Record<string, unknown> {
  try {
    return JSON.parse(step.configJson || '{}') as Record<string, unknown>
  } catch {
    return {}
  }
}

export function stepSubtitle(step: Step): string {
  const config = parseConfig(step)
  switch (step.type) {
    case 'HTTP': {
      const method = String(config.method ?? 'GET').toUpperCase()
      const url = String(config.url ?? '')
      return `${method} ${url}`.trim()
    }
    case 'SQL': {
      const sql = String(config.sql ?? '').replace(/\s+/g, ' ').trim()
      return sql.length > 64 ? `${sql.slice(0, 64)}…` : sql
    }
    case 'DELAY':
      return `等待 ${config.millis ?? 1000} ms`
    case 'SET_VAR':
      return '写入环境变量'
    case 'DELETE_VAR':
      return '删除环境变量'
    default:
      return step.code
  }
}

export function stepExtractionPreview(step: Step): string {
  try {
    const items = JSON.parse(step.extractionJson || '[]') as Array<{ target?: string; expression?: string }>
    if (!items.length) return ''
    const first = items[0]
    const target = first.target ?? 'var'
    const expr = first.expression ?? ''
    return `${target} = ${expr}`
  } catch {
    return ''
  }
}

export function stepAssertionPreview(step: Step): string {
  try {
    const items = JSON.parse(step.assertionJson || '[]') as Array<{ operator?: string; expected?: unknown; source?: string }>
    if (!items.length) return ''
    const first = items[0]
    return `${first.source ?? 'OUTPUT'} ${first.operator ?? 'EQUALS'} ${JSON.stringify(first.expected ?? '')}`
  } catch {
    return ''
  }
}

export function toStepCardView(step: Step, index: number): StepCardView {
  const extraction = stepExtractionPreview(step)
  const assertion = stepAssertionPreview(step)
  return {
    index,
    title: step.name || stepTypeLabel(step.type),
    subtitle: extraction || stepSubtitle(step),
    badge: stepTypeLabel(step.type),
    hasExtraction: Boolean(extraction),
    hasAssertion: Boolean(assertion)
  }
}

export function formatDuration(ms?: number): string {
  if (ms == null) return '-'
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}

export function statusIcon(status?: string): string {
  switch (status?.toUpperCase()) {
    case 'PASSED':
    case 'SUCCESS':
      return '✓'
    case 'FAILED':
      return '✕'
    case 'RUNNING':
      return '◐'
    default:
      return '○'
  }
}
