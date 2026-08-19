package com.kunling.scheduling.action.compilation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.kunling.scheduling.action.capability.application.CapabilityCatalog;
import com.kunling.scheduling.action.capability.domain.CapabilityManifest;
import com.kunling.scheduling.action.capability.domain.CapabilityRetrySafety;
import com.kunling.scheduling.action.capability.domain.CapabilitySideEffect;
import com.kunling.scheduling.action.compilation.application.ActionCompiler;
import com.kunling.scheduling.action.config.ActionProperties;
import com.kunling.scheduling.action.definition.domain.ActionDefinition;
import com.kunling.scheduling.action.definition.domain.ParameterSchema;
import com.kunling.scheduling.action.definition.domain.ParameterType;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TianjinStandardActionCompilationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void allSevenMigratedActionsCompileAgainstTheTianjinAtomicContract() throws Exception {
        List<CapabilityManifest> manifests = manifests();
        CapabilityCatalog catalog = new CapabilityCatalog() {
            public List<CapabilityManifest> listAll() { return manifests; }
            public Optional<CapabilityManifest> find(String key) {
                return manifests.stream().filter(item -> item.capabilityKey().equals(key)).findFirst();
            }
        };
        ActionCompiler compiler = new ActionCompiler(catalog, (key, version) -> Optional.empty(),
                new ActionProperties(new ActionProperties.Compiler(8, 500, 6, 524_288)),
                new JsonCodec(objectMapper));

        try (var files = Files.list(Path.of("src/main/resources/standard-actions"))) {
            var results = files.filter(path -> path.toString().endsWith(".json"))
                    .map(path -> compile(path, compiler))
                    .toList();
            assertThat(results).hasSize(7);
            assertThat(results).allSatisfy(result -> assertThat(result.success())
                    .withFailMessage(() -> "标准动作编译失败：" + result.issues())
                    .isTrue());
        }
    }

    private com.kunling.scheduling.action.compilation.domain.CompileResult compile(
            Path path, ActionCompiler compiler) {
        try {
            return compiler.compile(objectMapper.readValue(path.toFile(), ActionDefinition.class));
        } catch (Exception exception) {
            throw new AssertionError("无法加载 " + path.getFileName(), exception);
        }
    }

    private List<CapabilityManifest> manifests() {
        ParameterSchema text = schema(ParameterType.STRING, true);
        ParameterSchema optionalText = schema(ParameterType.STRING, false);
        ParameterSchema number = schema(ParameterType.NUMBER, true);
        ParameterSchema integer = schema(ParameterType.INTEGER, true);
        ParameterSchema bool = schema(ParameterType.BOOLEAN, true);
        ParameterSchema x = rangedNumber(-1500, 1500, "mm");
        ParameterSchema y = rangedNumber(-1500, 1500, "mm");
        ParameterSchema z = rangedNumber(0, 1500, "mm");
        ParameterSchema inlinePose = new ParameterSchema(ParameterType.OBJECT, false, null, List.of(), Map.of(
                "frame", enumText("BASE"), "unit", enumText("MILLIMETER_DEGREE"),
                "x", x, "y", y, "z", z, "rx", number, "ry", number, "rz", number), null);
        ParameterSchema pose = new ParameterSchema(ParameterType.OBJECT, true, null, List.of(), Map.of(
                "poseRef", optionalText, "inlinePose", inlinePose), null);

        Map<String, ParameterSchema> motion = new LinkedHashMap<>();
        motion.put("station", text); motion.put("point", optionalText); motion.put("poseRole", text);
        motion.put("pose", pose); motion.put("positionToleranceMm", number); motion.put("angleToleranceDeg", number);
        motion.put("settleMs", integer); motion.put("timeoutMs", integer); motion.put("pollMs", integer);
        motion.put("speedProfile", text); motion.put("collisionProfile", text);
        Map<String, ParameterSchema> vision = Map.of(
                "station", text, "recipe", text, "cameraId", text, "exposureMs", number,
                "gain", number, "timeoutMs", integer, "outputFormat", text, "simulatedPass", bool);

        return List.of(
                manifest("chassis.move", Map.of("target", text, "port", text, "speed", number),
                        List.of("chassis"), CapabilitySideEffect.PHYSICAL, CapabilityRetrySafety.VERIFY_BEFORE_RETRY, true, false),
                manifest("arm.move.linear", motion, List.of("arm"), CapabilitySideEffect.PHYSICAL,
                        CapabilityRetrySafety.VERIFY_BEFORE_RETRY, true, true),
                manifest("vision.verify.material", vision, List.of("vision"), CapabilitySideEffect.NONE,
                        CapabilityRetrySafety.SAFE, true, false),
                manifest("vision.verify.placement", vision, List.of("vision"), CapabilitySideEffect.NONE,
                        CapabilityRetrySafety.SAFE, true, false),
                manifest("vision.capture", vision, List.of("vision"), CapabilitySideEffect.NONE,
                        CapabilityRetrySafety.SAFE, false, false),
                manifest("gripper.open", required(Map.of("targetWidthMm", ParameterType.NUMBER,
                                "holdMs", ParameterType.INTEGER, "minDetectedWidth", ParameterType.NUMBER)),
                        List.of("gripper"), CapabilitySideEffect.PHYSICAL, CapabilityRetrySafety.VERIFY_BEFORE_RETRY, true, false),
                manifest("gripper.close", required(Map.of("targetWidthMm", ParameterType.NUMBER,
                                "holdMs", ParameterType.INTEGER, "minDetectedWidth", ParameterType.NUMBER,
                                "maxDetectedWidth", ParameterType.NUMBER, "gripForce", ParameterType.NUMBER)),
                        List.of("gripper"), CapabilitySideEffect.PHYSICAL, CapabilityRetrySafety.VERIFY_BEFORE_RETRY, true, false),
                manifest("gripper.verify.load", required(Map.of("minDetectedWidth", ParameterType.NUMBER,
                                "maxDetectedWidth", ParameterType.NUMBER, "stableForMs", ParameterType.INTEGER,
                                "pollMs", ParameterType.INTEGER, "requireForceFeedback", ParameterType.BOOLEAN,
                                "minForce", ParameterType.NUMBER, "expectedDetected", ParameterType.BOOLEAN)),
                        List.of("gripper"), CapabilitySideEffect.NONE, CapabilityRetrySafety.SAFE, true, false),
                manifest("chassis.verify.stopped", Map.of(), List.of("chassis"), CapabilitySideEffect.NONE,
                        CapabilityRetrySafety.SAFE, true, false),
                manifest("arm.verify.home", Map.of(), List.of("arm"), CapabilitySideEffect.NONE,
                        CapabilityRetrySafety.SAFE, true, false),
                manifest("system.fail", Map.of("message", text), List.of(), CapabilitySideEffect.NONE,
                        CapabilityRetrySafety.SAFE, true, false));
    }

    private Map<String, ParameterSchema> required(Map<String, ParameterType> parameters) {
        Map<String, ParameterSchema> result = new LinkedHashMap<>();
        parameters.forEach((name, type) -> result.put(name, schema(type, true)));
        return result;
    }

    private CapabilityManifest manifest(String key, Map<String, ParameterSchema> input, List<String> resources,
                                        CapabilitySideEffect sideEffect, CapabilityRetrySafety retrySafety,
                                        boolean safetyCritical, boolean motionSafety) {
        return new CapabilityManifest(key, "contract-" + key, input,
                Map.of("confirmed", schema(ParameterType.BOOLEAN, false),
                        "imageUri", schema(ParameterType.STRING, false)),
                resources, sideEffect, retrySafety, safetyCritical, motionSafety);
    }

    private ParameterSchema schema(ParameterType type, boolean required) {
        return new ParameterSchema(type, required, null, List.of(), Map.of(), null);
    }

    private ParameterSchema enumText(String value) {
        return new ParameterSchema(ParameterType.STRING, true, null, List.of(value), Map.of(), null);
    }

    private ParameterSchema rangedNumber(int minimum, int maximum, String unit) {
        return new ParameterSchema(ParameterType.NUMBER, true, unit, List.of(), Map.of(), null,
                BigDecimal.valueOf(minimum), BigDecimal.valueOf(maximum));
    }
}
