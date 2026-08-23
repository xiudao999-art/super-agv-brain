package com.kunling.scheduling.workflow.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** AGV流程中可编排的设备动作节点。 */
public enum NodeEnum {

    /** 控制AGV移动到指定库位、车库位或设备工位。 */
    MOVE("MOVE", "移动", "控制AGV移动到指定目标位置，完成后由设备状态反馈推进流程"),

    /** 控制机械臂从指定位置抓取物料或耗材。 */
    ARM_PICK("ARM.PICK", "机械臂取料", "控制机械臂移动到取料位并抓取指定物料或耗材"),

    /** 控制机械臂回到安全零位。 */
    ARM_HOME("ARM.HOME", "机械臂归零", "控制机械臂返回安全零位，为后续动作或AGV移动让出空间"),

    /** 控制机械臂将携带的物料放置到指定位置。 */
    ARM_PLACE("ARM.PLACE", "机械臂放料", "控制机械臂将物料或耗材放置到目标工位、料仓或贴标台");

    private final String code;
    private final String label;
    private final String description;

    NodeEnum(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    /** JSON请求和响应使用业务编码，例如ARM.PICK。 */
    @JsonValue
    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    /** 根据前端传入的业务编码转换枚举，忽略大小写。 */
    @JsonCreator
    public static NodeEnum fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的节点编码: " + code));
    }
}
