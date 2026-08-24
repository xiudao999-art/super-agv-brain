# 实验室配置接口文档

本文档面向前端联调，描述当前 `scheduling-app` 暴露的图片上传与实验室配置接口。接口以“空间配置版本”为编辑和发布单位，一份配置详情同时返回地图、通行节点、通行连接、机台和机台点位。

- 适用后端分支：`dev`
- 契约核对日期：`2026-08-24`
- 当前接口数量：23

## 1. 基本约定

### 1.1 服务地址

- 本地默认地址：`http://localhost:8081`
- 业务接口前缀：`/api`
- OpenAPI：`GET /v3/api-docs`
- 资源与实验室配置分组 OpenAPI：`GET /v3/api-docs/resource-config`

本文后续使用 `{baseUrl}` 表示服务地址，例如：

```text
{baseUrl}/api/lab-spaces
```

### 1.2 请求格式

- 除图片上传使用 `multipart/form-data` 外，请求体统一使用 `Content-Type: application/json`。
- 请求 JSON 不允许出现后端 DTO 未定义的字段，多传字段会返回 HTTP `400`。
- 所有编码字段只允许字母、数字、下划线和短横线：`^[A-Za-z0-9_-]+$`。
- 坐标 `x/y/z` 的单位固定为米，角度 `yaw/rx/ry/rz` 的单位固定为度。
- `speedLimit` 的单位为米/秒。
- `spaceId` 是 UUID 字符串；`configId`、节点 ID、机台 ID、点位 ID、连接 ID 和 `locationId` 是数字 ID。

### 1.3 统一响应结构

所有接口都返回统一结构，HTTP 状态码与响应体中的 `code` 一致。

```ts
export interface ApiResult<T> {
  code: number;
  message: string;
  data?: T;
}
```

查询成功示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

新增成功示例：

```json
{
  "code": 201,
  "message": "操作成功",
  "data": {
    "id": 101
  }
}
```

修改或删除成功没有业务数据，`data` 字段不会返回：

```json
{
  "code": 200,
  "message": "操作成功"
}
```

字段校验失败示例：

```json
{
  "code": 400,
  "message": "请求参数校验失败",
  "data": [
    {
      "field": "code",
      "message": "空间编码不能为空"
    }
  ]
}
```

业务冲突示例：

```json
{
  "code": 409,
  "message": "该空间已存在草稿"
}
```

### 1.4 HTTP 状态码

| HTTP 状态码 | 含义 | 前端处理建议 |
|---|---|---|
| `200` | 查询、修改、删除、校验或发布成功 | 读取 `data`；无数据接口只提示成功 |
| `201` | 空间、草稿或子项创建成功 | 使用响应中的新 ID，不要继续使用临时 ID |
| `400` | JSON 格式、字段格式、枚举值或业务入参错误 | 优先展示 `data[].field/message`，没有明细时展示 `message` |
| `404` | 空间、配置或子项不存在 | 刷新当前空间或配置数据 |
| `409` | 状态冲突、重复编码、跨配置引用或被其他对象引用 | 展示 `message` 并刷新当前配置 |
| `413` | 上传图片超过 10MB | 提示用户压缩图片后重试 |
| `500` | 未处理的服务端异常 | 展示通用错误并保留请求上下文供排查 |

## 2. 版本与编辑规则

配置状态只有以下三种：

| 状态 | 含义 | 是否允许修改子项 |
|---|---|---|
| `DRAFT` | 草稿 | 是 |
| `PUBLISHED` | 当前已发布版本 | 否 |
| `ARCHIVED` | 被新版本替换的历史版本 | 否 |

前端必须遵循以下规则：

1. 同一空间最多存在一个草稿和一个当前已发布版本。
2. 节点、连接、机台、点位和地图只允许修改草稿。
3. 发布新草稿后，原 `PUBLISHED` 版本自动变成 `ARCHIVED`。
4. 从已发布版本创建草稿时，后端会完整复制对象图，但所有子项都会获得新 ID。前端必须切换到响应中的新 `configId` 并重新加载详情。
5. 空间名称可以直接修改，后端会同步该空间的所有版本；空间编码创建后不可修改。
6. 配置详情是所有子项的唯一读取入口，没有单独的节点、连接、机台或点位列表接口。
7. 删除接口当前返回 HTTP `200`，不是 `204`。

推荐的页面状态选择逻辑：

```ts
const activeConfig = space.draft ?? space.published;
const editable = activeConfig?.status === 'DRAFT';
```

## 3. TypeScript 数据模型

以下类型可直接作为前端接口层的基础定义。

```ts
export type LabConfigStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type CoordinateFrame = 'MAP' | 'MACHINE';
export type LabLinkDirection = 'ONE_WAY' | 'BIDIRECTIONAL';

export interface LabMap {
  name: string;
  version: string;
  imageUrl: string;
}

export interface ImageUploadResult {
  imageUrl: string;
}

export interface LabConfigCounts {
  nodeCount: number;
  machineCount: number;
  pointCount: number;
  linkCount: number;
}

export interface LabConfigSummary {
  id: number;
  revision: number;
  status: LabConfigStatus;
  map: LabMap;
  counts: LabConfigCounts;
}

export interface LabSpaceSummary {
  id: string;
  code: string;
  name: string;
  published?: LabConfigSummary;
  draft?: LabConfigSummary;
}

export interface LabNode {
  id: number;
  code: string;
  name: string;
  type: string;
  locationId?: number;
  x: number;
  y: number;
  yaw: number;
}

export interface LabMachine {
  id: number;
  code: string;
  name: string;
  type: string;
  anchorX: number;
  anchorY: number;
  anchorYaw: number;
}

export interface LabPoint {
  id: number;
  machineId: number;
  locationId?: number;
  navNodeId?: number;
  code: string;
  name: string;
  type: string;
  frame: CoordinateFrame;
  x: number;
  y: number;
  z: number;
  rx: number;
  ry: number;
  rz: number;
}

export interface LabLink {
  id: number;
  code: string;
  startNodeId: number;
  endNodeId: number;
  direction: LabLinkDirection;
  speedLimit: number;
}

export interface LabConfigDetail {
  id: number;
  spaceId: string;
  spaceCode: string;
  spaceName: string;
  revision: number;
  status: LabConfigStatus;
  map: LabMap;
  nodes: LabNode[];
  machines: LabMachine[];
  points: LabPoint[];
  links: LabLink[];
}

export type LabMapPointKind = 'TRAFFIC_NODE' | 'MACHINE' | 'MACHINE_POINT';

export interface LabMapPoint {
  id: number;
  kind: LabMapPointKind;
  code: string;
  name: string;
  type: string;
  locationId?: number;
  x: number;
  y: number;
  yaw: number;
}

export interface LabValidationIssue {
  code: string;
  message: string;
  entityType: 'CONFIG' | 'OBJECT' | 'LINK';
  entityId: number;
}

export interface LabValidationResult {
  valid: boolean;
  issues: LabValidationIssue[];
}
```

`published`、`draft`、`locationId` 和 `navNodeId` 为 `null` 时不会出现在 JSON 中，前端应按可选字段处理。

## 4. 接口总览

| 方法 | 路径 | 说明 | 成功状态 |
|---|---|---|---|
| `POST` | `/api/files/images` | 上传地图图片 | `201` |
| `GET` | `/api/lab-spaces` | 查询空间、当前发布版本和草稿 | `200` |
| `POST` | `/api/lab-spaces` | 新增空间并创建首个草稿 | `201` |
| `PUT` | `/api/lab-spaces/{spaceId}` | 修改空间名称 | `200` |
| `POST` | `/api/lab-spaces/{spaceId}/drafts` | 从当前版本创建新草稿 | `201` |
| `GET` | `/api/lab-configs/{configId}` | 查询配置完整详情 | `200` |
| `GET` | `/api/lab-configs/{configId}/map-points` | 查询可直接绘制的地图点位列表 | `200` |
| `PUT` | `/api/lab-configs/{configId}/map` | 修改草稿地图图片信息 | `200` |
| `DELETE` | `/api/lab-configs/{configId}` | 删除草稿 | `200` |
| `POST` | `/api/lab-configs/{configId}/nodes` | 新增通行节点 | `201` |
| `PUT` | `/api/lab-configs/{configId}/nodes/{nodeId}` | 修改通行节点 | `200` |
| `DELETE` | `/api/lab-configs/{configId}/nodes/{nodeId}` | 删除通行节点 | `200` |
| `POST` | `/api/lab-configs/{configId}/machines` | 新增机台 | `201` |
| `PUT` | `/api/lab-configs/{configId}/machines/{machineId}` | 修改机台 | `200` |
| `DELETE` | `/api/lab-configs/{configId}/machines/{machineId}` | 删除机台 | `200` |
| `POST` | `/api/lab-configs/{configId}/points` | 新增机台点位 | `201` |
| `PUT` | `/api/lab-configs/{configId}/points/{pointId}` | 修改机台点位 | `200` |
| `DELETE` | `/api/lab-configs/{configId}/points/{pointId}` | 删除机台点位 | `200` |
| `POST` | `/api/lab-configs/{configId}/links` | 新增通行连接 | `201` |
| `PUT` | `/api/lab-configs/{configId}/links/{linkId}` | 修改通行连接 | `200` |
| `DELETE` | `/api/lab-configs/{configId}/links/{linkId}` | 删除通行连接 | `200` |
| `POST` | `/api/lab-configs/{configId}/validate` | 校验配置 | `200` |
| `POST` | `/api/lab-configs/{configId}/publish` | 发布配置 | `200` |

### 4.1 上传地图图片

```http
POST /api/files/images
Content-Type: multipart/form-data
```

表单字段：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `file` | file | 是 | PNG、JPEG、GIF 或 WEBP；最大 10MB |

成功响应：

```json
{
  "code": 201,
  "message": "操作成功",
  "data": {
    "imageUrl": "/files/550e8400-e29b-41d4-a716-446655440000.png"
  }
}
```

`imageUrl` 是可直接访问的相对地址，也是新增空间或修改地图时应提交的字段。前端如需完整地址，可拼接 `{baseUrl}`。文件为空或内容不是支持的图片格式时返回 `400`；超过 10MB 返回 `413`。

## 5. 空间与版本接口

### 5.1 查询空间列表

```http
GET /api/lab-spaces
```

请求参数：无。

响应 `data` 类型：`LabSpaceSummary[]`。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": "49e589fa-649f-4d08-9806-cf697dad2599",
      "code": "SPACE-LAB-A",
      "name": "实验室 A",
      "published": {
        "id": 100,
        "revision": 1,
        "status": "PUBLISHED",
        "map": {
          "name": "实验室总览地图",
          "version": "V1.0",
          "imageUrl": "/files/lab-a-v1.png"
        },
        "counts": {
          "nodeCount": 2,
          "machineCount": 1,
          "pointCount": 1,
          "linkCount": 1
        }
      },
      "draft": {
        "id": 101,
        "revision": 2,
        "status": "DRAFT",
        "map": {
          "name": "实验室总览地图",
          "version": "V1.1",
          "imageUrl": "/files/lab-a-v1.1.png"
        },
        "counts": {
          "nodeCount": 2,
          "machineCount": 1,
          "pointCount": 1,
          "linkCount": 1
        }
      }
    }
  ]
}
```

说明：

- `published` 或 `draft` 不存在时，对应字段不返回。
- 该接口只返回当前发布版本和当前草稿，不返回 `ARCHIVED` 历史版本。
- `counts` 由子项实时统计，不是独立维护的字段。

### 5.2 新增空间并创建首个草稿

```http
POST /api/lab-spaces
Content-Type: application/json
```

请求体：

```json
{
  "code": "SPACE-LAB-A",
  "name": "实验室 A",
  "map": {
    "name": "实验室总览地图",
    "version": "V1.0",
    "imageUrl": "/files/lab-a-v1.png"
  }
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `code` | string | 是 | 最长 64；只能包含字母、数字、下划线、短横线；全系统空间编码唯一 |
| `name` | string | 是 | 最长 128 |
| `map.name` | string | 是 | 最长 128 |
| `map.version` | string | 是 | 最长 64 |
| `map.imageUrl` | string | 是 | 最长 512；使用图片上传接口返回的相对地址 |

成功响应：

```json
{
  "code": 201,
  "message": "操作成功",
  "data": {
    "spaceId": "49e589fa-649f-4d08-9806-cf697dad2599",
    "configId": 101,
    "revision": 1,
    "status": "DRAFT"
  }
}
```

常见错误：空间编码重复返回 `409`。

### 5.3 修改空间名称

```http
PUT /api/lab-spaces/{spaceId}
Content-Type: application/json
```

请求体：

```json
{
  "name": "实验室 A（东区）"
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `name` | string | 是 | 最长 128 |

成功返回 HTTP `200`，无 `data`。空间不存在返回 `404`。

### 5.4 创建新草稿

```http
POST /api/lab-spaces/{spaceId}/drafts
```

请求体：无。

成功响应：

```json
{
  "code": 201,
  "message": "操作成功",
  "data": {
    "spaceId": "49e589fa-649f-4d08-9806-cf697dad2599",
    "configId": 102,
    "revision": 2,
    "status": "DRAFT"
  }
}
```

说明：

- 优先从当前 `PUBLISHED` 版本复制地图、节点、连接、机台和点位。
- 复制后所有对象和连接 ID 都会变化，内部引用会自动重连到新 ID。
- 已存在草稿时返回 `409`。
- 空间不存在时返回 `404`。

## 6. 配置详情与地图接口

### 6.1 查询配置完整详情

```http
GET /api/lab-configs/{configId}
```

响应 `data` 类型：`LabConfigDetail`。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 101,
    "spaceId": "49e589fa-649f-4d08-9806-cf697dad2599",
    "spaceCode": "SPACE-LAB-A",
    "spaceName": "实验室 A",
    "revision": 1,
    "status": "DRAFT",
    "map": {
      "name": "实验室总览地图",
      "version": "V1.0",
      "imageUrl": "/files/lab-a-v1.png"
    },
    "nodes": [
      {
        "id": 201,
        "code": "N01",
        "name": "立库出口",
        "type": "NAVIGATION",
        "locationId": 10,
        "x": 11.82,
        "y": 6.11,
        "yaw": 90.0
      }
    ],
    "machines": [
      {
        "id": 301,
        "code": "M01",
        "name": "贴标机台",
        "type": "LABELER",
        "anchorX": 28.6,
        "anchorY": 6.8,
        "anchorYaw": 180.0
      }
    ],
    "points": [
      {
        "id": 401,
        "machineId": 301,
        "locationId": 11,
        "navNodeId": 201,
        "code": "P01",
        "name": "放料点",
        "type": "ARM_PLACE",
        "frame": "MACHINE",
        "x": 0.42,
        "y": -0.135,
        "z": 0.88,
        "rx": 0.0,
        "ry": 0.0,
        "rz": 180.0
      }
    ],
    "links": [
      {
        "id": 501,
        "code": "L01",
        "startNodeId": 201,
        "endNodeId": 202,
        "direction": "BIDIRECTIONAL",
        "speedLimit": 0.6
      }
    ]
  }
}
```

配置不存在返回 `404`。

### 6.2 查询地图点位列表

```http
GET /api/lab-configs/{configId}/map-points
```

每个 `configId` 唯一确定一个空间地图版本。接口返回该地图中所有可绘制对象，响应 `data` 类型为 `LabMapPoint[]`。

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 201,
      "kind": "TRAFFIC_NODE",
      "code": "N01",
      "name": "立库出口",
      "type": "NAVIGATION",
      "locationId": 10,
      "x": 11.82,
      "y": 6.11,
      "yaw": 90.0
    },
    {
      "id": 301,
      "kind": "MACHINE",
      "code": "M01",
      "name": "贴标机台",
      "type": "LABELER",
      "x": 3.2,
      "y": 4.1,
      "yaw": 90.0
    },
    {
      "id": 401,
      "kind": "MACHINE_POINT",
      "code": "P01",
      "name": "放料点",
      "type": "ARM_PLACE",
      "x": 3.335,
      "y": 4.52,
      "yaw": -90.0
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | 原配置对象 ID，可与配置详情中的节点、机台或点位对应 |
| `kind` | string | `TRAFFIC_NODE`、`MACHINE` 或 `MACHINE_POINT`，前端可据此选择图标和颜色 |
| `code` | string | 对象编码 |
| `name` | string | 对象名称 |
| `type` | string | 可扩展业务类型代码 |
| `locationId` | number | 可选的关联库位 ID；没有绑定时不返回 |
| `x`、`y` | number | 统一换算后的地图坐标，单位为米 |
| `yaw` | number | 统一换算后的地图朝向角，单位为度，范围为 `-180` 至 `180` |

坐标规则：

- 通行节点直接使用节点的地图坐标。
- 机台使用机台锚点的地图坐标。
- `frame=MAP` 的机台点位直接使用点位坐标。
- `frame=MACHINE` 的机台点位由后端结合所属机台锚点完成旋转和平移，前端不得再次转换。
- 该接口面向二维地图绘制，因此不返回点位的 `z/rx/ry`；需要完整六维位姿时使用配置详情接口。
- 草稿、已发布和归档配置都可以查询；配置不存在返回 `404`，配置数据无法正确投影时返回 `409`。

### 6.3 修改草稿地图图片信息

```http
PUT /api/lab-configs/{configId}/map
Content-Type: application/json
```

请求体：

```json
{
  "name": "实验室总览地图",
  "version": "V1.1",
  "imageUrl": "/files/lab-a-v1.1.png"
}
```

字段约束与创建空间时的 `map` 相同。成功返回 HTTP `200`，无 `data`。

非草稿配置返回 `409`；配置不存在返回 `404`。

### 6.4 删除草稿

```http
DELETE /api/lab-configs/{configId}
```

成功返回 HTTP `200`，无 `data`。只有 `DRAFT` 可以删除，其他状态返回 `409`。

## 7. 通行节点接口

### 7.1 请求字段

新增与修改使用相同请求体：

```json
{
  "code": "N01",
  "name": "立库出口",
  "type": "NAVIGATION",
  "locationId": 10,
  "x": 11.82,
  "y": 6.11,
  "yaw": 90.0
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `code` | string | 是 | 最长 64；编码格式固定；同一配置的节点编码唯一 |
| `name` | string | 是 | 最长 128 |
| `type` | string | 是 | 最长 64；可扩展业务代码，例如 `NAVIGATION`、`PATH`、`WAITING` |
| `locationId` | number | 否 | 已存在的库位 ID；同一配置的节点中不能重复绑定同一库位 |
| `x` | number | 是 | 米；整数最多 8 位，小数最多 4 位 |
| `y` | number | 是 | 米；整数最多 8 位，小数最多 4 位 |
| `yaw` | number | 是 | 度；范围 `-180` 至 `180`，小数最多 4 位 |

节点坐标系固定为地图坐标系，前端不传 `frame`。

### 7.2 新增节点

```http
POST /api/lab-configs/{configId}/nodes
```

成功返回 HTTP `201`：

```json
{
  "code": 201,
  "message": "操作成功",
  "data": {
    "id": 201
  }
}
```

### 7.3 修改节点

```http
PUT /api/lab-configs/{configId}/nodes/{nodeId}
```

成功返回 HTTP `200`，无 `data`。

### 7.4 删除节点

```http
DELETE /api/lab-configs/{configId}/nodes/{nodeId}
```

成功返回 HTTP `200`，无 `data`。节点仍被连接或点位的 `navNodeId` 引用时返回 `409`，前端应先删除或修改引用关系。

## 8. 机台接口

### 8.1 请求字段

新增与修改使用相同请求体：

```json
{
  "code": "M01",
  "name": "贴标机台",
  "type": "LABELER",
  "anchorX": 28.6,
  "anchorY": 6.8,
  "anchorYaw": 180.0
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `code` | string | 是 | 最长 64；编码格式固定；同一配置的机台编码唯一 |
| `name` | string | 是 | 最长 128 |
| `type` | string | 是 | 最长 64；可扩展业务代码，例如 `LABELER` |
| `anchorX` | number | 是 | 米；整数最多 8 位，小数最多 4 位 |
| `anchorY` | number | 是 | 米；整数最多 8 位，小数最多 4 位 |
| `anchorYaw` | number | 是 | 度；范围 `-180` 至 `180`，建议最多 4 位小数 |

机台锚点固定使用地图坐标系，前端不传 `frame`。

### 8.2 新增机台

```http
POST /api/lab-configs/{configId}/machines
```

成功返回 HTTP `201` 和 `data.id`。

### 8.3 修改机台

```http
PUT /api/lab-configs/{configId}/machines/{machineId}
```

成功返回 HTTP `200`，无 `data`。

### 8.4 删除机台

```http
DELETE /api/lab-configs/{configId}/machines/{machineId}
```

成功返回 HTTP `200`，无 `data`。机台仍被点位的 `machineId` 引用时返回 `409`。

## 9. 机台点位接口

### 9.1 请求字段

新增与修改使用相同请求体：

```json
{
  "machineId": 301,
  "locationId": 11,
  "navNodeId": 201,
  "code": "P01",
  "name": "放料点",
  "type": "ARM_PLACE",
  "frame": "MACHINE",
  "x": 0.42,
  "y": -0.135,
  "z": 0.88,
  "rx": 0.0,
  "ry": 0.0,
  "rz": 180.0
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `machineId` | number | 是 | 当前配置内的机台 ID |
| `locationId` | number | 否 | 已存在的库位 ID；同一配置的点位中不能重复绑定同一库位 |
| `navNodeId` | number | 否 | 当前配置内的通行节点 ID |
| `code` | string | 是 | 最长 64；编码格式固定；同一配置的点位编码唯一 |
| `name` | string | 是 | 最长 128 |
| `type` | string | 是 | 最长 64；可扩展业务代码，例如 `ARM_PLACE` |
| `frame` | string | 是 | 只能是 `MAP` 或 `MACHINE` |
| `x`、`y`、`z` | number | 是 | 米；整数最多 8 位，小数最多 4 位 |
| `rx`、`ry`、`rz` | number | 是 | 度；范围 `-180` 至 `180`，建议最多 4 位小数 |

### 9.2 新增点位

```http
POST /api/lab-configs/{configId}/points
```

成功返回 HTTP `201` 和 `data.id`。

### 9.3 修改点位

```http
PUT /api/lab-configs/{configId}/points/{pointId}
```

成功返回 HTTP `200`，无 `data`。

### 9.4 删除点位

```http
DELETE /api/lab-configs/{configId}/points/{pointId}
```

成功返回 HTTP `200`，无 `data`。

## 10. 通行连接接口

### 10.1 请求字段

新增与修改使用相同请求体：

```json
{
  "code": "L01",
  "startNodeId": 201,
  "endNodeId": 202,
  "direction": "BIDIRECTIONAL",
  "speedLimit": 0.6
}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| `code` | string | 是 | 最长 64；编码格式固定；同一配置的连接编码唯一 |
| `startNodeId` | number | 是 | 当前配置内的通行节点 ID |
| `endNodeId` | number | 是 | 当前配置内的通行节点 ID；不能与起点相同 |
| `direction` | string | 是 | `ONE_WAY` 或 `BIDIRECTIONAL` |
| `speedLimit` | number | 是 | 必须大于 0；整数最多 5 位，小数最多 3 位；单位 m/s |

### 10.2 新增连接

```http
POST /api/lab-configs/{configId}/links
```

成功返回 HTTP `201` 和 `data.id`。

### 10.3 修改连接

```http
PUT /api/lab-configs/{configId}/links/{linkId}
```

成功返回 HTTP `200`，无 `data`。

### 10.4 删除连接

```http
DELETE /api/lab-configs/{configId}/links/{linkId}
```

成功返回 HTTP `200`，无 `data`。

## 11. 校验与发布接口

### 11.1 校验配置

```http
POST /api/lab-configs/{configId}/validate
```

请求体：无。

校验通过：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "valid": true,
    "issues": []
  }
}
```

校验不通过仍返回 HTTP `200`，通过 `data.valid` 判断：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "valid": false,
    "issues": [
      {
        "code": "DUPLICATE_LOCATION_BINDING",
        "message": "同一配置内库位绑定重复",
        "entityType": "OBJECT",
        "entityId": 203
      }
    ]
  }
}
```

校验问题代码：

| code | entityType | 含义 |
|---|---|---|
| `MISSING_MAP` | `CONFIG` | 地图名称、版本或文件引用不完整 |
| `INCOMPLETE_OBJECT` | `OBJECT` | 对象编码、名称或类型不完整 |
| `DUPLICATE_OBJECT_CODE` | `OBJECT` | 同类对象编码重复，编码比较不区分大小写 |
| `INVALID_OBJECT_KIND` | `OBJECT` | 对象类别不受支持 |
| `INVALID_MAP_POSE` | `OBJECT` | 节点或机台的地图坐标不完整或角度无效 |
| `INVALID_POINT_POSE` | `OBJECT` | 点位坐标系或六维位姿无效 |
| `DUPLICATE_LOCATION_BINDING` | `OBJECT` | 同一类对象重复绑定库位 |
| `UNKNOWN_LOCATION` | `OBJECT` | 绑定的库位不存在 |
| `BROKEN_MACHINE_REFERENCE` | `OBJECT` | 点位所属机台不存在或不属于当前配置 |
| `BROKEN_NAV_REFERENCE` | `OBJECT` | 点位关联节点不存在或不属于当前配置 |
| `INCOMPLETE_LINK` | `LINK` | 连接编码为空 |
| `DUPLICATE_LINK_CODE` | `LINK` | 连接编码重复，编码比较不区分大小写 |
| `SELF_LINK` | `LINK` | 起点和终点相同 |
| `BROKEN_LINK_START` | `LINK` | 连接起点不存在或不属于当前配置 |
| `BROKEN_LINK_END` | `LINK` | 连接终点不存在或不属于当前配置 |
| `INVALID_SPEED_LIMIT` | `LINK` | 限速为空或不大于 0 |
| `INVALID_DIRECTION` | `LINK` | 连接方向不受支持 |

`entityType=OBJECT` 可能指节点、机台或点位。前端可用 `entityId` 在详情的 `nodes`、`machines`、`points` 中依次定位。

### 11.2 发布配置

```http
POST /api/lab-configs/{configId}/publish
```

请求体：无。

成功响应 `data` 类型为 `LabConfigSummary`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 101,
    "revision": 2,
    "status": "PUBLISHED",
    "map": {
      "name": "实验室总览地图",
      "version": "V1.1",
      "imageUrl": "/files/lab-a-v1.1.png"
    },
    "counts": {
      "nodeCount": 2,
      "machineCount": 1,
      "pointCount": 1,
      "linkCount": 1
    }
  }
}
```

说明：

- 发布接口会在事务内重新校验，不能依赖前一次校验结果。
- 校验失败返回 `409`，消息为“配置校验未通过，不能发布”。前端应先调用校验接口展示具体问题。
- 只有 `DRAFT` 可以发布；重复发布返回 `409`。
- 发布成功后应重新请求空间列表和配置详情。

## 12. 前端推荐联调流程

### 12.1 页面初始化

1. 调用 `GET /api/lab-spaces` 加载空间列表。
2. 展示时优先使用 `draft`，没有草稿时使用 `published`。
3. 用户进入三个页签时，使用选中版本的 `id` 调用 `GET /api/lab-configs/{configId}`。
4. 地图、通行规则、机台与点位三个页签共享同一份 `LabConfigDetail`，不要分别维护互相独立的数据副本。
5. 需要在地图底图上绘制标记时，调用 `GET /api/lab-configs/{configId}/map-points`，直接使用返回的 `x/y/yaw`。

### 12.2 进入编辑

1. 如果当前已有 `draft`，直接加载 `draft.id`。
2. 如果只有 `published`，先调用 `POST /api/lab-spaces/{spaceId}/drafts`。
3. 使用响应中的新 `configId` 重新加载详情。
4. 只有 `status === 'DRAFT'` 时显示或启用新增、编辑、删除、修改地图和发布按钮。

### 12.3 保存子项

1. 新增成功后读取 `data.id`。
2. 修改或删除成功后，建议重新请求一次配置详情，避免本地引用关系与服务端不一致。
3. 收到 `409` 时显示后端 `message`，随后刷新详情。

### 12.4 校验并发布

1. 调用 `POST /api/lab-configs/{configId}/validate`。
2. `valid=false` 时按 `entityType + entityId` 定位表格行并展示问题。
3. `valid=true` 时调用发布接口。
4. 发布成功后刷新空间列表；原发布版本可能已经归档，旧草稿 ID 也不再可编辑。

## 13. 当前前端开发版字段映射

当前 React 页面仍使用本地 Mock 字段。接入接口时请按下表转换，不要把展示字段直接发送给后端。

### 13.1 地图信息弹窗

| 当前表单字段 | 后端字段 | 处理方式 |
|---|---|---|
| `spaceCode` | `code` | 直接映射；注意当前 Mock 使用了非标准连字符 `‑`，应改为普通短横线 `-` |
| `spaceName` | `name` | 直接映射 |
| `mapName` | `map.name` | 直接映射 |
| `mapVersion` | `map.version` | 直接映射 |
| `mapFile` | `map.imageUrl` | 先调用 `POST /api/files/images` 上传图片，再提交响应中的 `data.imageUrl` |
| `coordUnit` | 不提交 | 单位固定为米和度；当前页面“米/弧度”应调整为“米/度” |

### 13.2 通行节点弹窗

| 当前表单字段 | 后端字段 | 处理方式 |
|---|---|---|
| `nodeNo` | `code` | 直接映射并使用普通短横线 `-` |
| `nodeName` | `name` | 直接映射 |
| 当前缺少 | `type` | 页面需要增加节点类型字段或由明确的业务选项赋值 |
| `belongSpace` | 不提交 | 使用 URL 中的 `configId` 确定归属 |
| `mapX` | `x` | 字符串转 number |
| `mapY` | `y` | 字符串转 number |
| `mapTheta` | `yaw` | 字符串转 number，单位为度 |
| `trafficRule` | 不提交 | 由节点类型、连接方向和限速组合展示 |

### 13.3 通行连接弹窗

| 当前表单字段 | 后端字段 | 处理方式 |
|---|---|---|
| `linkNo` | `code` | 直接映射 |
| `belongSpace` | 不提交 | 使用 URL 中的 `configId` 确定归属 |
| `startPoint` | `startNodeId` | 改为节点下拉选择并提交节点数字 ID |
| `endPoint` | `endNodeId` | 改为节点下拉选择并提交节点数字 ID |
| `direction` | `direction` | 使用 `ONE_WAY`、`BIDIRECTIONAL` 选项，不提交中文文案 |
| `speedLimit` | `speedLimit` | 字符串转正数 |

### 13.4 机台弹窗

| 当前表单字段 | 后端字段 | 处理方式 |
|---|---|---|
| `machineNo` | `code` | 直接映射 |
| `machineName` | `name` | 直接映射 |
| 当前缺少 | `type` | 页面需要增加机台类型字段或由明确的业务选项赋值 |
| `belongSpace` | 不提交 | 使用 URL 中的 `configId` 确定归属 |
| `anchorX` | `anchorX` | 字符串转 number |
| `anchorY` | `anchorY` | 字符串转 number |
| `mapTheta` | `anchorYaw` | 字符串转 number，单位为度 |

### 13.5 机台点位弹窗

| 当前表单字段 | 后端字段 | 处理方式 |
|---|---|---|
| `pointNo` | `code` | 直接映射 |
| 当前缺少 | `name` | 页面需要增加点位名称字段 |
| `pointType` | `type` | 直接映射 |
| `belongMachine` | `machineId` | 改为机台下拉选择并提交机台数字 ID |
| `belongSpace` | 不提交 | 使用 URL 中的 `configId` 确定归属 |
| `coordSystem` | `frame` | 映射为 `MAP` 或 `MACHINE`，不提交中文文案 |
| `xyz` | `x/y/z` | 不使用拼接字符串，拆成三个 number 字段 |
| `rxryrz` | `rx/ry/rz` | 不使用拼接字符串，拆成三个 number 字段 |
| `relateAgvPoint` | `navNodeId` | 改为节点下拉选择并提交节点数字 ID；允许不选 |
| 当前缺少 | `locationId` | 可选；需要绑定库位时增加库位选择器 |

`EditSpaceResourceModal` 当前混合了多类资源字段，无法直接对应单个后端接口。建议节点、机台、点位分别复用各自新增表单并根据 ID 调用对应的 `PUT` 接口。

## 14. 暂不提供的能力

首期接口不包含以下能力，前端不要调用或预留不存在的地址：

- 地图包（ZIP、SLAM 原始文件）上传；首期只接收可直接预览的图片。
- 路径模板和“进入路径管理”接口。
- 外围资源配置接口。
- 试运行记录接口。
- 管理员审批流接口。
- 历史版本列表接口。
