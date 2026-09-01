# scheduling-app 数据库脚本

`scheduling-app` 不会在启动时自动建表或写入业务目录，以下脚本需要由数据库管理员人工执行。

## Action 业务场景目录

- 空库初始化：执行 `create/action_scene_catalog_schema.sql`。
- 已有数据库升级：执行 `alter/20260901_01_add_action_scene_catalog.sql`。
- 可选初始目录：建表后执行 `data/action_scene_catalog_initial_data.sql`。

初始目录脚本可以重复执行，只补齐缺失记录，不会覆盖已人工修改的展示名称、排序和启用状态。业务场景与原子操作目录仅用于页面编排，不代表机器人当前会话具备相应执行能力。
