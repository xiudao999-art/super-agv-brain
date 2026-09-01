# Scheduling Application Context

调度应用上下文向页面提供业务配置和流程入口，并协调 Action 与 Workflow 模块的公开能力。

## Language

**Business Scene**:
供页面选择和编排 Action 的业务分类；它只表达业务意图，不代表机器人当前具备执行能力。
_Avoid_: Main Action、Robot Capability

**Operation**:
Action 定义中可组合的稳定原子操作协议标识，同一 Operation 可以属于多个 Business Scene。
_Avoid_: Business Scene、Device Command

**Robot Capability**:
机器人当前会话注册的可执行 Operation 及约束，是运行时能力校验的事实来源。
_Avoid_: Business Scene、Operation Catalog
