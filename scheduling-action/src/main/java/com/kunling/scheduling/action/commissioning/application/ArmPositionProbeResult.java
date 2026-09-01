package com.kunling.scheduling.action.commissioning.application;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;
import java.time.Instant;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
@Schema(description = "机械臂当前两套位姿；仅用于调试页面的相对位置计算")
public class ArmPositionProbeResult {
    String robotId;
    Instant capturedAt;
    int armMoveRequestType;
    int speedPercent;
    CartesianPose armPoseXYZRxRyRz;
    JointPose armPoseJ1J2J3J4J5J6;

    @ConstructorProperties({"robotId", "capturedAt", "armMoveRequestType", "speedPercent",
            "armPoseXYZRxRyRz", "armPoseJ1J2J3J4J5J6"})
    public ArmPositionProbeResult(String robotId, Instant capturedAt, int armMoveRequestType,
                                  int speedPercent, CartesianPose armPoseXYZRxRyRz,
                                  JointPose armPoseJ1J2J3J4J5J6) {
        this.robotId = robotId;
        this.capturedAt = capturedAt;
        this.armMoveRequestType = armMoveRequestType;
        this.speedPercent = speedPercent;
        this.armPoseXYZRxRyRz = armPoseXYZRxRyRz;
        this.armPoseJ1J2J3J4J5J6 = armPoseJ1J2J3J4J5J6;
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class CartesianPose {
        double x;
        double y;
        double z;
        double rx;
        double ry;
        double rz;

        @ConstructorProperties({"x", "y", "z", "rx", "ry", "rz"})
        public CartesianPose(double x, double y, double z, double rx, double ry, double rz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class JointPose {
        double j1;
        double j2;
        double j3;
        double j4;
        double j5;
        double j6;

        @ConstructorProperties({"j1", "j2", "j3", "j4", "j5", "j6"})
        public JointPose(double j1, double j2, double j3, double j4, double j5, double j6) {
            this.j1 = j1;
            this.j2 = j2;
            this.j3 = j3;
            this.j4 = j4;
            this.j5 = j5;
            this.j6 = j6;
        }
    }
}
