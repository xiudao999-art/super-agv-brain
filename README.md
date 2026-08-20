# Kunling Scheduling

坤灵机器人调度主应用，采用 Maven 模块化单体架构。Action 模块维护“当前 Action 定义”，将它与设备联调参数、本次业务入参解析为不可变执行包，再一次性下发给设备适配层。

## 核心边界

- Action 和状态机属于上游；Action 负责动作结构、参数解析、合理性校验、异常策略编码和整包下发，不维护第二套业务状态机。
- 设备适配层属于下游；按 `phases` 顺序执行子动作，先消化可确定的原子异常，再按包内业务策略处理。
- Action 没有业务版本，只有 `DRAFT / ACTIVE / DISABLED` 和防止并发覆盖的 `revision`。
- 开始执行前可编辑步骤与参数；开始后当前联调任务永久只读，修改必须新建联调任务。
- 发生断线、超时或物理结果不确定时进入 `UNKNOWN_HOLD`，只查询证据，不自动重放物理动作。

## 当前下游协议能力

支持七种主动作：`MOVE`、`ARM.PICK`、`ARM.PLACE`、`ARM.PICK_BATCH`、`ARM.PLACE_BATCH`、`ARM.HOME`、`VISION.CAPTURE`。主 Action 可以自由调整子动作顺序或重复 N 次，但子动作必须属于下游已注册协议清单。

## 工程结构

```text
kunling-scheduling/
├─ scheduling-app/                 # 唯一可执行主应用与部署配置
├─ scheduling-action/              # Action 定义、联调参数、整包执行与 Robot Bridge
└─ scheduling-agvFlow/             # 状态机/流程模块
```

Action 模块内部按 `definition`、`commissioning`、`execution`、`robotbridge` 分区，协议转换只集中在 `ActionPackageAssembler`，执行层不再保留逐原子 HTTP 调用链。

## 数据库

`kunling_action_schema.sql` 是不可变建库基线，任何数据库变更都只能追加到 `db/alter/`。全新库应先执行基线，再按时间顺序执行全部 ALTER：

```sql
create database kunling character set utf8mb4 collate utf8mb4_0900_ai_ci;
use kunling;
source scheduling-action/src/main/resources/db/create/kunling_action_schema.sql;
source scheduling-action/src/main/resources/db/alter/20260820_01_dynamic_action_package.sql;
```

已有库执行前先备份，再人工审核并执行尚未应用的 `db/alter/20260820_01_dynamic_action_package.sql`。脚本会直接删除旧 Action 表及数据，不会转换旧 JSON；备份是唯一恢复来源。应用保持 `ddl-auto=none`，不使用 Flyway，不在启动时写初始数据。

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

详细协议和决策见 [Action 动态整包联调说明](scheduling-action/docs/phase-1-fixed-action-integration.md) 与 [ADR-0003](scheduling-action/docs/adr/0003-modularize-scheduling-application.md)。
