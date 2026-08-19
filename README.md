# Kunling Scheduling

坤灵机器人调度主应用，采用 Maven 模块化单体架构。一期执行链只启用四类固定动作包：`MOVE`、`ARM.HOME`、`ARM.PICK`、`ARM.PLACE`。动态 Action、组合动作与 Canvas 代码继续保留，待下一期接入执行链。

## 工程结构

```text
kunling-scheduling/
├─ pom.xml                         # 聚合父工程与统一依赖基线
├─ scheduling-app/                 # 唯一可执行主应用、部署配置和模块装配
└─ scheduling-action/              # Action 业务模块
   ├─ src/main/java/.../action/    # 领域、应用、适配器及模块配置入口
   ├─ .../action/robotbridge/      # TCP 协议、机器人会话、命令与事件路由
   ├─ src/main/resources/db/       # 手工 CREATE 基线与后续 ALTER 脚本
   ├─ src/main/resources/static/   # Action 管理页面
   └─ src/test/                    # Action Java 与 Canvas 测试
```

主应用只显式装配 `ActionModuleConfiguration`。Robot Bridge 是 `scheduling-action` 内部的基础设施包，仍通过小型传输接口与固定动作应用服务隔离，业务状态机不依赖 Socket 实现。

详细约束见 [模块化单体架构](scheduling-action/docs/adr/0003-modularize-scheduling-application.md)。

## 技术基线

- Java 8（本机基线 `D:\java\jdk1.8.0_201`）
- Spring Boot 2.7.18
- Maven 3.8.8 Wrapper
- MySQL 8
- 数据库结构与初始数据由开发人员手工维护
- HTTP 端口默认 `8081`
- 机器人 TCP 端口默认 `8080`
- Knife4j 接口文档：`http://localhost:8081/doc.html`

## 本地构建与运行

先创建数据库，再由开发人员手工执行一次全量初始化脚本：

```sql
create database kunling character set utf8mb4 collate utf8mb4_0900_ai_ci;
use kunling;
source scheduling-action/src/main/resources/db/create/kunling_action_schema.sql;
```

初始化脚本包含当前完整表结构和七条天津标准 Action 草稿。已有数据库禁止重复执行
CREATE 脚本，后续变更统一新增到 `scheduling-action/src/main/resources/db/alter`，并由开发人员
审核、备份、执行和登记。应用保持 `ddl-auto=none`，不会自动建表、改表或补写初始数据。
示例数据库名必须与 `application.yml` 中的 JDBC URL 保持一致。

完整规范见 `scheduling-action/src/main/resources/db/README.md`。

数据源连接信息和机器人 TCP 监听地址直接维护在 `scheduling-app/src/main/resources/application.yml`。其中：

- `server.port`：下游 HTTP 服务端口，默认 `8081`；
- `kunling.action.robot-bridge.bind-address`：下游 TCP 监听网卡，默认 `0.0.0.0`；
- `kunling.action.robot-bridge.port`：下游 TCP 监听端口，默认 `8080`；
- `kunling.action.robot-bridge.enabled`：是否启动机器人 TCP Bridge。

固定动作白名单、TCP 租约、消息大小、编译限制等一期业务安全边界仍统一维护在 `ActionModuleDefaults`，不随部署配置漂移。

```powershell
$env:JAVA_HOME='D:\java\jdk1.8.0_201'
.\mvnw.cmd verify
java -jar .\scheduling-app\target\kunling-scheduling.jar
```

Action 管理页面：`http://localhost:8081/`。

Knife4j 接口文档：`http://localhost:8081/doc.html`；OpenAPI JSON：`http://localhost:8081/v3/api-docs/action`。

## 一期上游接入

上游机器人客户端主动连接下游 `kunling.action.robot-bridge` 配置的 TCP 地址（默认 `8080`），使用“一行一个 UTF-8 JSON”的协议完成：

- `REGISTER` / `REGISTER_ACK`；
- `PING` / `PONG`；
- `COMMAND` 完整动作包；
- `ACTION_EVENT` 状态事件；
- `QUERY_ACTION` / `ACTION_STATUS` 状态核对。

一期不修改上游代码，不主动取消已下发动作，也不逐条下发原子动作。完整契约、接口示例和安全语义见 [一期固定动作包联调说明](scheduling-action/docs/phase-1-fixed-action-integration.md)。原子目录与动态编排要求已移至二期范围。

- [一期固定动作包联调说明](scheduling-action/docs/phase-1-fixed-action-integration.md)
- [二期动态 Action 上游接口草案](scheduling-action/docs/upstream-integration-requirements.md)

## 验证

```powershell
$env:JAVA_HOME='D:\java\jdk1.8.0_201'
.\mvnw.cmd verify
$tests = Get-ChildItem .\scheduling-action\src\test\js\*.test.js | ForEach-Object FullName
node --test $tests
```

## 职责划分

- `scheduling-app`：进程启动、部署配置、模块依赖与装配。
- `scheduling-action/robotbridge`：Action 模块内部的 TCP 监听、会话、协议编解码与消息路由。
- `scheduling-action`：固定模板、执行持久化、安全状态机、Robot Bridge，以及保留的 Action 管理能力。
- 上游客户端：执行下游发出的完整动作包；本项目不修改其代码。
- 工作流系统：发起已发布主 Action，并消费执行状态。
