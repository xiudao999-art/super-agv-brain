package com.kunling.scheduling.action.exceptionmapping.infrastructure;

import com.kunling.scheduling.action.config.JsonCodec;
import com.kunling.scheduling.action.exceptionmapping.domain.ActionErrorMappingRule;
import com.kunling.scheduling.action.exceptionmapping.domain.ErrorMappingRuleStatus;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;

/** 当前映射规则表；完整规则 JSON 保留扩展字段，查询字段单独建列。 */
@Entity
@Table(name = "action_error_mapping_rule")
public class ActionErrorMappingRuleEntity {
    @Id
    @Column(name = "rule_id", length = 128, nullable = false)
    private String ruleId;

    @Column(name = "profile_id", length = 128, nullable = false)
    private String profileId;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "revision", nullable = false)
    private long revision;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ErrorMappingRuleStatus status;

    @Lob
    @Column(name = "rule_json", nullable = false, columnDefinition = "longtext")
    private String ruleJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActionErrorMappingRuleEntity() {
    }

    public ActionErrorMappingRuleEntity(ActionErrorMappingRule rule, JsonCodec jsonCodec, Instant now) {
        this.ruleId = rule.ruleId();
        this.profileId = rule.profileId();
        this.priority = rule.priority();
        this.revision = 1L;
        this.status = ErrorMappingRuleStatus.DRAFT;
        this.ruleJson = jsonCodec.write(rule);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(ActionErrorMappingRule rule, JsonCodec jsonCodec, Instant now) {
        this.profileId = rule.profileId();
        this.priority = rule.priority();
        this.ruleJson = jsonCodec.write(rule);
        this.status = ErrorMappingRuleStatus.DRAFT;
        this.revision++;
        this.updatedAt = now;
    }

    public void changeStatus(ErrorMappingRuleStatus target, Instant now) {
        this.status = target;
        this.revision++;
        this.updatedAt = now;
    }

    public ActionErrorMappingRule rule(JsonCodec jsonCodec) {
        return jsonCodec.read(ruleJson, ActionErrorMappingRule.class);
    }

    public String getRuleId() { return ruleId; }
    public String getProfileId() { return profileId; }
    public int getPriority() { return priority; }
    public long getRevision() { return revision; }
    public ErrorMappingRuleStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
