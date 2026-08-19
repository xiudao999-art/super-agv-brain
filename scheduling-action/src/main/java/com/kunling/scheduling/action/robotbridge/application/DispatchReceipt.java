package com.kunling.scheduling.action.robotbridge.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import java.time.Instant;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class DispatchReceipt {
    String sessionId;
    String messageId;
    Instant sentAt;
    @ConstructorProperties({"sessionId", "messageId", "sentAt"})
    public DispatchReceipt(
            String sessionId,
            String messageId,
            Instant sentAt
    ) {
        this.sessionId = sessionId;
        this.messageId = messageId;
        this.sentAt = sentAt;
    }

}
