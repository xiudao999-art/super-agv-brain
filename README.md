# Kunling Scheduling

坤灵机器人调度主应用，采用 Maven 模块化单体架构。Action 模块维护“当前 Action 定义”，将它与设备联调参数集解析为不可变执行包，再一次性下发给设备适配层。

## 核心边界

- Action 和状态机属于上游；Action 负责动作结构、参数解析、合理性校验、业务策略编译和整包下发，不维护第二套业务状态机。
- 设备适配层属于下游；只理解线协议、原子操作和执行指令，不依赖 Action 或流程业务上下文。
- Action 没有业务版本，只有 `DRAFT / ACTIVE / DISABLED` 和防止并发覆盖的 `revision`。
- Action 有运行实例时禁止编辑步骤与参数；运行结束后允许继续修改，单次执行始终使用冻结快照和 `packageHash`。
- 发生断线、超时或物理结果不确定时进入 `UNKNOWN_HOLD`，只查询证据，不自动重放物理动作。

## 当前下游协议能力

当前线协议固定为 2.0。下游按 `operation + schemaHash` 注册原子能力并声明策略特性；Action 将动态模板编译为自解释 `executionPlan`，下游不根据主 Action 名称编写业务分支。

## 工程结构

```text
kunling-scheduling/
├─ scheduling-app/                 # 唯一可执行主应用与部署配置
├─ scheduling-action/              # Action 定义、联调参数、整包执行与 Robot Bridge
└─ scheduling-agvFlow/             # 状态机/流程模块
```

Action 模块内部按 `definition`、`commissioning`、`execution`、`robotbridge` 分区，协议转换只集中在 `ActionPackageAssembler`，执行层不再保留逐原子 HTTP 调用链。

## 数据库

`kunling_action_schema.sql` 是 Action 2.0 全新建库基线：

```sql
create database kunling character set utf8mb4 collate utf8mb4_0900_ai_ci;
use kunling;
source scheduling-action/src/main/resources/db/create/kunling_action_schema.sql;
```

现阶段不保留旧 Action 数据结构的运行时适配；开发库切换时应先备份，再重建本模块表并重新配置 Action。应用保持 `ddl-auto=none`，不使用 Flyway，不在启动时写初始数据。

## 构建与运行

```powershell
$env:JAVA_HOME='D:\java\jdk1.8.0_201'
.\mvnw.cmd verify
$tests = Get-ChildItem .\scheduling-action\src\test\js\*.test.js | ForEach-Object FullName
node --test $tests
java -jar .\scheduling-app\target\kunling-scheduling.jar
```

- Action 配置与联调页：`http://localhost:8081/`
- Knife4j：`http://localhost:8081/doc.html`
- Robot Bridge：默认 TCP `8080`，一行一个 UTF-8 JSON

上下游开发、协议和验收的唯一核心基线见 [Action 模块—下游模块联调清单](scheduling-action/docs/Action模块-下游模块联调清单.md)。
