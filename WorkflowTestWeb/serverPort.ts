import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

/** 与 WorkflowTestServer application.yml 中 workflowtest.data-dir 保持一致 */
export function resolvePortFile(): string {
  const root = process.env.LOCALAPPDATA || path.join(os.homedir(), '.workflowtest')
  return path.join(root, 'WorkflowTest', 'data', 'server.port')
}

export function readServerPort(fallback = 8080): number {
  try {
    const content = fs.readFileSync(resolvePortFile(), 'utf8').trim()
    const port = Number.parseInt(content, 10)
    if (!Number.isFinite(port) || port <= 0) {
      return fallback
    }
    return port
  } catch {
    return fallback
  }
}

export function serverBaseUrl(fallbackPort = 8080): string {
  return `http://localhost:${readServerPort(fallbackPort)}`
}
