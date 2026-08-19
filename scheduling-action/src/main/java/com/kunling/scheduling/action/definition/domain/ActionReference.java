package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionReference {
    String actionKey;
    String version;
    @ConstructorProperties({"actionKey", "version"})
    public ActionReference(
            String actionKey,
            String version
    ) {
        this.actionKey = actionKey;
        this.version = version;
    }

}
