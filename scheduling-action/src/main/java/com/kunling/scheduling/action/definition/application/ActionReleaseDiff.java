package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.util.List;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionReleaseDiff {
    String actionKey;
    String fromVersion;
    String toVersion;
    List<ActionChange> changes;
    @ConstructorProperties({"actionKey", "fromVersion", "toVersion", "changes"})
    public ActionReleaseDiff(
            String actionKey,
            String fromVersion,
            String toVersion,
            List<ActionChange> changes
    ) {
        this.actionKey = actionKey;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.changes = changes;
    }

}
