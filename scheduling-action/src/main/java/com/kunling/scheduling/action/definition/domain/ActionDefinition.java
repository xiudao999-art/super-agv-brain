package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.kunling.scheduling.action.config.ImmutableCollections;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.util.List;

/** 单个 Action 的当前动态定义；运行时通过定义行锁保证执行期间不可编辑。 */
@Schema(description = "当前 Action 的动态配置定义")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionDefinition {
    @Schema(description = "Action 定义标识；新建时由服务端生成")
    String id;
    @Schema(description = "Action 名称")
    String name;
    @Schema(description = "是否允许执行；新建时固定为 false")
    boolean enabled;
    @Schema(description = "整个 Action 超时时间，单位毫秒")
    int timeoutMs;
    @Schema(description = "按顺序执行的原子操作步骤")
    List<ActionStepDefinition> steps;

    @ConstructorProperties({"id", "name", "enabled", "timeoutMs", "steps"})
    public ActionDefinition(String id,
                            String name,
                            boolean enabled,
                            int timeoutMs,
                            List<ActionStepDefinition> steps) {
        this.id = normalizeToNull(id);
        this.name = normalize(name);
        this.enabled = enabled;
        this.timeoutMs = timeoutMs;
        this.steps = steps == null ? ImmutableCollections.listOf() : ImmutableCollections.copyList(steps);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeToNull(String value) {
        String normalized = normalize(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }
}
