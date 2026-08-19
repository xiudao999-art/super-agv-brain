package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionDependency {
    String actionKey;
    String version;
    String planHash;
    @ConstructorProperties({"actionKey", "version", "planHash"})
    public ActionDependency(
            String actionKey,
            String version,
            String planHash
    ) {
        this.actionKey = actionKey;
        this.version = version;
        this.planHash = planHash;
    }

}
