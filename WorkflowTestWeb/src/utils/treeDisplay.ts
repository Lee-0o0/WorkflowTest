export type TreeNodeKind = 'project' | 'group' | 'workflow' | 'hook'

export type HookTypeLabel = 'BEFORE_EACH_GROUP' | 'BEFORE_GROUP' | 'AFTER_GROUP'

const META: Record<TreeNodeKind, { title: string }> = {
  project: { title: '项目' },
  group: { title: '组' },
  workflow: { title: '工作流' },
  hook: { title: '钩子' }
}

export function treeNodeTitle(kind: TreeNodeKind): string {
  return META[kind].title
}

export function treeNodeIconClass(kind: TreeNodeKind): string {
  return `node-type-icon node-type-${kind}`
}

export function hookTreeLabel(hookType: HookTypeLabel): string {
  const map: Record<HookTypeLabel, string> = {
    BEFORE_EACH_GROUP: '项目钩子 · 每组前',
    BEFORE_GROUP: '组前置钩子',
    AFTER_GROUP: '组后置钩子'
  }
  return map[hookType] ?? '钩子'
}
