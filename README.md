# Kunling Scheduling

坤灵机器人调度主应用，采用 Maven 模块化单体架构。当前已接入 Action 管理模块，负责主 Action、全局组合动作、Canvas 配置、不可变发布、执行计划及逐原子能力编排；后续业务模块以同级 Maven 模块接入主应用。

## 工程结构

```text
kunling-scheduling/
├─ pom.xml                         # 聚合父工程与统一依赖基线
├─ scheduling-app/                 # 唯一可执行主应用、部署配置和模块装配
└─ scheduling-action/              # Action 业务模块
   ├─ src/main/java/.../action/    # 领域、应用、适配器及模块配置入口
   ├─ src/main/resources/db/       # Action 数据库迁移
   ├─ src/main/resources/static/   # Action 管理页面
   └─ src/test/                    # Action Java 与 Canvas 测试
```

主应用通过 `ActionModuleConfiguration` 显式装配 Action 模块，不扫描模块内部实现。Action 模块不依赖 `scheduling-app`，因此后续模块不会与 Action 代码混入同一源码集。

详细约束见 [模块化单体架构](docs/adr/0003-modularize-scheduling-application.md)。

## 技术基线

- Java 21
- Spring Boot 3.5.x
- Maven 3.8.8 Wrapper
- MySQL 8
- Flyway 管理数据库结构
- HTTP 端口默认 `8080`

## 本地构建与运行

先创建数据库，表结构由 Flyway 自动维护：

```sql
create database kunling_action character set utf8mb4 collate utf8mb4_0900_ai_ci;
```

推荐使用调度主应用环境变量：

```text
SCHEDULING_DB_URL
SCHEDULING_DB_USERNAME
SCHEDULING_DB_PASSWORD
```

原 `ACTION_DB_URL`、`ACTION_DB_USERNAME`、`ACTION_DB_PASSWORD` 仍可作为兼容变量使用。

```powershell
$env:JAVA_HOME='D:\java\jdk21'
.\mvnw.cmd verify
java -jar .\scheduling-app\target\kunling-scheduling.jar
```

Action 管理页面：`http://localhost:8080/`。

## 上游接入

默认 `UPSTREAM_ENABLED=false`，主应用可以启动，但 Action 模块不能同步或执行原子 Action。上游实现约定接口后配置：

```text
UPSTREAM_ENABLED=true
UPSTREAM_BASE_URL=http://upstream-host:port
```

Action 模块会定时同步能力目录，也可调用 `POST /api/capabilities/synchronize` 手动同步。上游只提供当前原子能力目录；能力契约 Hash 由 Action 模块计算并固定在发布计划中。原子节点调用统一使用 `consumeId` 去重和查询。

- [上游团队交付包与验收清单](docs/upstream-delivery-checklist.md)
- [上游原子 Action 服务接口需求](docs/upstream-integration-requirements.md)

## 验证

```powershell
$env:JAVA_HOME='D:\java\jdk21'
.\mvnw.cmd verify
node --test .\scheduling-action\src\test\js
```

## 职责划分

- `scheduling-app`：进程启动、部署配置、模块依赖与装配。
- `scheduling-action`：Action CRUD、编译、发布、Canvas、执行状态机与上游调用编排。
- 上游服务：提供原子 Action 目录和执行接口；本项目不修改其代码，只通过 Adapter 访问。
- 工作流系统：发起已发布主 Action，并消费执行状态。
