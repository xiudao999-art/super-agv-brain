package com.kunling.scheduling.action.fixed.infrastructure;

import com.kunling.scheduling.action.shared.JsonCodec;
import com.kunling.scheduling.action.robotbridge.application.RobotActionEvent;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "robot_action_event")
public class RobotActionEventEntity {

    @Id
    @Column(name = "message_id", length = 64, nullable = false)
    private String messageId;

    @Column(name = "action_instance_id", length = 128, nullable = false)
    private String actionInstanceId;

    @Column(name = "robot_id", length = 128, nullable = false)
    private String robotId;

    @Column(name = "message_type", length = 32, nullable = false)
    private String messageType;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence;

    @Column(name = "event_state", length = 32, nullable = false)
    private String eventState;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "longtext")
    private String payloadJson;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected RobotActionEventEntity() {
    }

    public RobotActionEventEntity(RobotActionEvent event, JsonCodec jsonCodec, Instant receivedAt) {
        this.messageId = event.messageId();
        this.actionInstanceId = event.actionInstanceId();
        this.robotId = event.robotId();
        this.messageType = event.messageType();
        this.eventSequence = event.sequence();
        this.eventState = event.state().name();
        this.payloadJson = jsonCodec.write(event);
        this.receivedAt = receivedAt;
    }
}
