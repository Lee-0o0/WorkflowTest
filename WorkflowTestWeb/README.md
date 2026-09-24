# WorkflowTestWeb

Vue 3 前端（TestFlow UI），通过 HTTP 调用本地 `WorkflowTestServer`。

## 界面结构

- **左侧一级导航**：项目管理 / 全局变量 / 执行历史
- **项目树**：项目 → 组 → 工作流（Hook 不在树中展开，在工作流编辑区展示）
- **中间工作区**：工作流步骤编排 / 全局变量 / 执行历史
- **右侧 Inspector**：当前选中步骤的配置面板
- **顶部**：保存、执行当前工作流

## 开发

1. 启动后端：

```bash
cd ..
mvn -pl WorkflowTestServer spring-boot:run
```

2. 启动前端：

```bash
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。Vite 会从 `%LOCALAPPDATA%/WorkflowTest/data/server.port`（或 `~/.workflowtest/WorkflowTest/data/server.port`）读取后端端口并代理 `/api`；若文件不存在则回退到 `8080`。

## 生产

```bash
npm run build
```

构建产物在 `dist/`，可由 `WorkflowTestServer` 静态资源目录托管。
