package com.kunling.scheduling.workflow.resp;

import lombok.Data;

@Data
public class RobotAlarmRuleItemResp {

    private Long ruleId;

    private String itemType;

    private String content;
}
