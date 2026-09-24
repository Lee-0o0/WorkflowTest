import { defineConfig, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import http from 'node:http'
import path from 'node:path'
import { readServerPort, resolvePortFile, serverBaseUrl } from './serverPort'

function apiProxyPlugin(): Plugin {
  let lastLoggedPort: number | undefined

  const logProxyTarget = (log: { info: (msg: string) => void; warn: (msg: string) => void }) => {
    const port = readServerPort()
    const portFile = resolvePortFile()
    if (lastLoggedPort !== port) {
      if (fs.existsSync(portFile)) {
        log.info(`API 代理目标: ${serverBaseUrl()}（来自 ${portFile}）`)
      } else {
        log.warn(`未找到 ${portFile}，API 代理暂用 http://localhost:${port}（请先启动 WorkflowTestServer）`)
      }
      lastLoggedPort = port
    }
  }

  return {
    name: 'workflowtest-api-proxy',
    configureServer(server) {
      logProxyTarget(server.config.logger)

      const portFile = resolvePortFile()
      const portDir = path.dirname(portFile)
      if (fs.existsSync(portDir)) {
        fs.watch(portDir, (_, filename) => {
          if (filename === 'server.port') {
            lastLoggedPort = undefined
            logProxyTarget(server.config.logger)
          }
        })
      }

      // 勿挂载在 '/api' 前缀下，否则 req.url 会变成 /projects 而非 /api/projects
      server.middlewares.use((req, res, next) => {
        const requestPath = req.url ?? ''
        if (!requestPath.startsWith('/api')) {
          next()
          return
        }

        const port = readServerPort()
        const proxyReq = http.request(
          {
            hostname: 'localhost',
            port,
            path: requestPath,
            method: req.method,
            headers: {
              ...req.headers,
              host: `localhost:${port}`,
            },
          },
          (proxyRes) => {
            res.writeHead(proxyRes.statusCode ?? 502, proxyRes.headers)
            proxyRes.pipe(res)
          },
        )

        proxyReq.on('error', (error) => {
          if (!res.headersSent) {
            res.statusCode = 502
            res.setHeader('Content-Type', 'text/plain; charset=utf-8')
          }
          res.end(`WorkflowTestServer 不可用（端口 ${port}）: ${error.message}`)
        })

        req.pipe(proxyReq)
      })
    },
  }
}

export default defineConfig({
  plugins: [vue(), apiProxyPlugin()],
  server: {
    port: 5173,
  },
  build: {
    outDir: 'dist',
  },
})
