package com.kunling.scheduling.app.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exception_handling_rule_item")
@Schema(description = "异常处置规程子项")
public class ExceptionHandlingRuleItem extends AppBaseEntity {
    private Long ruleId;
    @Schema(description = "SYSTEM_ACTION系统自动执行；RELEASE_CONDITION恢复放行条件")
    private ExceptionRuleItemType itemType;
    @Schema(description = "类型内顺序")
    private Integer itemSeq;
    @Schema(description = "执行步骤或放行条件内容")
    private String content;
}
