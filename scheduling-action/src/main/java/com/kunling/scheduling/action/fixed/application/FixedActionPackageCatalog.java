package com.kunling.scheduling.action.fixed.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.kunling.scheduling.action.fixed.domain.FixedActionType;
import com.kunling.scheduling.action.fixed.domain.MaterializedFixedActionPackage;

/** 固定动作模板目录；调用方只能提交业务参数，不能替换受控的 phases。 */
public interface FixedActionPackageCatalog {

    MaterializedFixedActionPackage materialize(FixedActionType actionType, JsonNode input);
}
