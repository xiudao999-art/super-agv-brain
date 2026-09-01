(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  root.ActionApi = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  /**
   * 当前 Action 接口大部分使用 ApiResult<T>，协议目录仍直接返回对象。
   * 统一在边界拆包，页面内部只处理业务数据，避免每个交互重复判断响应结构。
   */
  function unwrap(body, httpOk, httpStatus) {
    const wrapped = body && typeof body === "object" && !Array.isArray(body)
      && Object.prototype.hasOwnProperty.call(body, "code")
      && Object.prototype.hasOwnProperty.call(body, "message")
      && Object.prototype.hasOwnProperty.call(body, "data");
    const businessOk = !wrapped || Number(body.code) === 200;
    if (!httpOk || !businessOk) {
      const message = wrapped && body.message
        ? body.message
        : typeof body === "string" && body.trim()
          ? body.trim()
          : `请求失败（HTTP ${httpStatus || "未知"}）`;
      const error = new Error(message);
      error.status = httpStatus;
      error.payload = body;
      throw error;
    }
    return wrapped ? body.data : body;
  }

  async function request(fetchImpl, url, options) {
    if (typeof fetchImpl !== "function") throw new TypeError("fetchImpl 必须是函数。");
    const requestOptions = Object.assign({}, options || {});
    requestOptions.headers = Object.assign({}, requestOptions.headers || {});
    if (requestOptions.body && typeof requestOptions.body !== "string") {
      requestOptions.headers["Content-Type"] = "application/json";
      requestOptions.body = JSON.stringify(requestOptions.body);
    }

    const response = await fetchImpl(url, requestOptions);
    if (response.status === 204) return null;
    const contentType = response.headers && response.headers.get
      ? response.headers.get("content-type") || "" : "";
    const body = contentType.includes("json") ? await response.json() : await response.text();
    return unwrap(body, response.ok, response.status);
  }

  return { request, unwrap };
});
