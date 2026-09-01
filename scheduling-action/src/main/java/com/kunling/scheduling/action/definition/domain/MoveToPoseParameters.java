package com.kunling.scheduling.action.definition.domain;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;
import lombok.experimental.Accessors;

import java.beans.ConstructorProperties;

/** MOVE_TO_POSE 的 JSON 参数声明；下游仍负责最终设备参数预检。 */
@Schema(description = "MOVE_TO_POSE 参数")
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MoveToPoseParameters {
    String station;
    String poseRole;
    String point;
    ArmPose pose;
    Double positionToleranceMm;
    Double angleToleranceDeg;
    Integer settleMs;
    Integer timeoutMs;
    Integer pollMs;
    String frame;
    String speedProfile;
    String collisionProfile;

    @ConstructorProperties({"station", "poseRole", "point", "pose", "positionToleranceMm",
            "angleToleranceDeg", "settleMs", "timeoutMs", "pollMs", "frame",
            "speedProfile", "collisionProfile"})
    public MoveToPoseParameters(String station,
                                String poseRole,
                                String point,
                                ArmPose pose,
                                Double positionToleranceMm,
                                Double angleToleranceDeg,
                                Integer settleMs,
                                Integer timeoutMs,
                                Integer pollMs,
                                String frame,
                                String speedProfile,
                                String collisionProfile) {
        this.station = station;
        this.poseRole = poseRole;
        this.point = point;
        this.pose = pose;
        this.positionToleranceMm = positionToleranceMm;
        this.angleToleranceDeg = angleToleranceDeg;
        this.settleMs = settleMs;
        this.timeoutMs = timeoutMs;
        this.pollMs = pollMs;
        this.frame = frame;
        this.speedProfile = speedProfile;
        this.collisionProfile = collisionProfile;
    }

    /** 页面使用不可直接执行的空工位模板，避免调试时误下发有效目标。 */
    public static MoveToPoseParameters editorTemplate() {
        return new MoveToPoseParameters(
                "", "", null,
                new ArmPose(0.00D, 0.00D, 0.00D, 0.00D, 0.00D, 0.00D),
                2.00D, 1.00D, 200, 10_000, 50,
                "BASE", "NORMAL", "NORMAL");
    }

    /** 笛卡尔坐标和姿态均使用小数类型，允许 JSON 传入两位小数。 */
    @Schema(description = "机械臂笛卡尔位姿")
    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    public static class ArmPose {
        Double x;
        Double y;
        Double z;
        Double rx;
        Double ry;
        Double rz;

        @ConstructorProperties({"x", "y", "z", "rx", "ry", "rz"})
        public ArmPose(Double x, Double y, Double z, Double rx, Double ry, Double rz) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }
    }
}
