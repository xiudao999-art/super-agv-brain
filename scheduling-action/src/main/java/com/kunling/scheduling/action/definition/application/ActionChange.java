package com.kunling.scheduling.action.definition.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionChange {
    String path;
    ChangeKind kind;
    String before;
    String after;
    ChangeRisk risk;
    @ConstructorProperties({"path", "kind", "before", "after", "risk"})
    public ActionChange(
            String path,
            ChangeKind kind,
            String before,
            String after,
            ChangeRisk risk
    ) {
        this.path = path;
        this.kind = kind;
        this.before = before;
        this.after = after;
        this.risk = risk;
    }


    public enum ChangeKind { ADDED, REMOVED, MODIFIED }

    public enum ChangeRisk { NORMAL, MEDIUM, HIGH }
}
