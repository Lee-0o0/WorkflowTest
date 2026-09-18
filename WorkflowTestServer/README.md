# WorkflowTestServer

WorkflowTestServer 是集中管理公司测试资产的独立后端服务。Desktop 通过 HTTPS API 访问它，Engine 不依赖本项目。

已实现：

- 用户初始化、用户管理、BCrypt 密码和 JWT 登录。
- 系统管理员与项目级 RBAC。
- 项目成员管理。
- 项目、组、工作流集中管理。
- 项目、组、工作流三级环境变量集中管理，并固化进发布快照。
- 工作流 JSON 草稿、revision 乐观锁。
- 不可变发布版本、SHA-256 校验和及执行包下载。
- PostgreSQL + MyBatis-Plus + Flyway。

## 权限

项目角色：`PROJECT_ADMIN`、`TEST_DEVELOPER`、`TEST_EXECUTOR`、`VIEWER`。

- 项目管理员：成员及所有资产管理。
- 测试开发：编辑、发布和执行。
- 测试执行：读取并下载执行包。
- 只读成员：只允许查看定义和版本，不能获取正式执行包。

## 启动

先创建 PostgreSQL 数据库和账号，然后设置环境变量：

```powershell
$env:WORKFLOWTEST_DB_URL = 'jdbc:postgresql://localhost:5432/workflowtest'
$env:WORKFLOWTEST_DB_USERNAME = 'workflowtest'
$env:WORKFLOWTEST_DB_PASSWORD = 'replace-me'
$env:WORKFLOWTEST_JWT_SECRET = 'replace-with-a-random-secret-at-least-32-bytes'
$env:WORKFLOWTEST_ADMIN_PASSWORD = 'replace-in-production'
mvn -pl WorkflowTestServer spring-boot:run
```

本地开发也可以先执行 `docker compose -f docker-compose.server.yml up -d`，再运行根目录的 `run-server.ps1`。

首次启动且用户表为空时创建管理员。默认开发账号为 `admin / ChangeMe123!`，生产环境必须通过环境变量修改。

登录：

```http
POST /api/auth/login
Content-Type: application/json

{"username":"admin","password":"ChangeMe123!"}
```

后续请求添加：

```http
Authorization: Bearer <token>
```
