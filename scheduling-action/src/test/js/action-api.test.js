const test = require("node:test");
const assert = require("node:assert/strict");
const actionApi = require("../../main/resources/static/action-api.js");

function response(body, { ok = true, status = 200, contentType = "application/json" } = {}) {
  return {
    ok,
    status,
    headers: { get: () => contentType },
    json: async () => body,
    text: async () => String(body)
  };
}

test("统一响应只向页面返回 data", async () => {
  const result = await actionApi.request(async () => response({
    code: 200,
    message: "操作成功",
    data: [{ robotId: "R01" }]
  }), "/api/robots");
  assert.deepEqual(result, [{ robotId: "R01" }]);
});

test("协议目录的裸对象保持原样", async () => {
  const catalog = { protocolVersion: "2.0", operationSuggestions: ["MOVE_TO_POSE"] };
  assert.deepEqual(await actionApi.request(async () => response(catalog), "/catalog"), catalog);
});

test("业务失败保留后端错误消息", async () => {
  await assert.rejects(
    actionApi.request(async () => response({ code: 409, message: "Action 正在执行", data: null }, {
      ok: false,
      status: 409
    }), "/api/actions/1"),
    /Action 正在执行/
  );
});

test("请求体自动序列化为 JSON", async () => {
  let captured;
  await actionApi.request(async (_, options) => {
    captured = options;
    return response({ code: 200, message: "操作成功", data: null });
  }, "/api/actions", { method: "POST", body: { name: "ARM.HOME" } });
  assert.equal(captured.headers["Content-Type"], "application/json");
  assert.equal(captured.body, '{"name":"ARM.HOME"}');
});
