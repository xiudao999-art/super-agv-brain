package com.kunling.scheduling.action.definition.infrastructure;

import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ActionStepDefinition;
import com.kunling.scheduling.action.config.JsonCodec;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.time.Instant;
import java.beans.ConstructorProperties;
import java.util.List;

/** Action 定义持久化；名称和启用态独立列存储，JSON 仅保存可执行内容。 */
@Entity
@Table(name = "action_definition")
public class ActionDefinitionEntity {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Lob
    @Column(name = "definition_json", nullable = false, columnDefinition = "longtext")
    private String definitionJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ActionDefinitionEntity() {
    }

    public ActionDefinitionEntity(String id, ActionDefinition definition, JsonCodec jsonCodec, Instant now) {
        this.id = id;
        this.name = definition.name();
        this.enabled = false;
        this.definitionJson = encodeContent(definition, jsonCodec);
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(ActionDefinition definition, JsonCodec jsonCodec, Instant now) {
        this.name = definition.name();
        this.definitionJson = encodeContent(definition, jsonCodec);
        this.updatedAt = now;
    }

    public void changeEnabled(boolean target, Instant now) {
        this.enabled = target;
        this.updatedAt = now;
    }

    public ActionDefinition definition(JsonCodec jsonCodec) {
        DefinitionContent content = jsonCodec.read(definitionJson, DefinitionContent.class);
        return new ActionDefinition(id, name, enabled, content.timeoutMs, content.steps);
    }

    private String encodeContent(ActionDefinition definition, JsonCodec jsonCodec) {
        return jsonCodec.write(new DefinitionContent(definition.timeoutMs(), definition.steps()));
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** definition_json 的稳定最小结构。 */
    public static class DefinitionContent {
        private final int timeoutMs;
        private final List<ActionStepDefinition> steps;

        @ConstructorProperties({"timeoutMs", "steps"})
        public DefinitionContent(int timeoutMs, List<ActionStepDefinition> steps) {
            this.timeoutMs = timeoutMs;
            this.steps = steps;
        }

        public int getTimeoutMs() { return timeoutMs; }
        public List<ActionStepDefinition> getSteps() { return steps; }
    }
}
