package com.kunling.scheduling.action.compilation.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ActionGroupReference {
    String actionKey;
    String actionVersion;
    String referenceStepId;
    String displayName;
    @ConstructorProperties({"actionKey", "actionVersion", "referenceStepId", "displayName"})
    public ActionGroupReference(
            String actionKey,
            String actionVersion,
            String referenceStepId,
            String displayName
    ) {
        this.actionKey = actionKey;
        this.actionVersion = actionVersion;
        this.referenceStepId = referenceStepId;
        this.displayName = displayName;
    }

}
