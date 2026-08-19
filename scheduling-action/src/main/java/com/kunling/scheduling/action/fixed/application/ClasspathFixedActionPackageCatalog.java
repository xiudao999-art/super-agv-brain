package com.kunling.scheduling.action.fixed.application;

import com.kunling.scheduling.action.shared.ImmutableCollections;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;
import java.beans.ConstructorProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.action.fixed.domain.FixedActionType;
import com.kunling.scheduling.action.fixed.domain.MaterializedFixedActionPackage;
import com.kunling.scheduling.action.shared.JsonCodec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Component
public class ClasspathFixedActionPackageCatalog implements FixedActionPackageCatalog {

    private static final String RESOURCE_DIRECTORY = "fixed-action-packages/";
    private static final String ACTION_VERSION = "1.0";
    private static final Map<FixedActionType, Set<String>> ALLOWED_INPUTS = ImmutableCollections.mapOf(
            FixedActionType.MOVE, ImmutableCollections.setOf("pointName", "port", "speed", "pose", "arrival"),
            FixedActionType.ARM_HOME, ImmutableCollections.setOf("station"),
            FixedActionType.ARM_PICK, ImmutableCollections.setOf("station", "point", "graspProfile", "expectedMaterial"),
            FixedActionType.ARM_PLACE, ImmutableCollections.setOf("station", "point", "releaseProfile")
    );

    private final ObjectMapper objectMapper;
    private final JsonCodec jsonCodec;
    private final Map<FixedActionType, TemplateDocument> templates;

    public ClasspathFixedActionPackageCatalog(ObjectMapper objectMapper, JsonCodec jsonCodec) {
        this.objectMapper = objectMapper;
        this.jsonCodec = jsonCodec;
        this.templates = loadTemplates();
    }

    @Override
    public MaterializedFixedActionPackage materialize(FixedActionType actionType, JsonNode input) {
        ObjectNode safeInput = requireObject(input);
        rejectUnknownFields(actionType, safeInput);

        TemplateDocument template = templates.get(actionType);
        ObjectNode mainAction = template.mainAction().deepCopy();
        switch (actionType) {
            case MOVE:
                materializeMove(mainAction, safeInput);
                break;
            case ARM_HOME:
                injectContext(mainAction, safeInput, "GLOBAL", null);
                break;
            case ARM_PICK:
                injectContext(mainAction, safeInput, "PICK_01", "graspProfile");
                break;
            case ARM_PLACE:
                injectContext(mainAction, safeInput, "PLACE_01", "releaseProfile");
                break;
            default:
                throw new IllegalArgumentException("不支持的固定动作类型：" + actionType);
        }

        ObjectNode commandInput = objectMapper.createObjectNode();
        // MainAction 的首字母大写是既有线协议的一部分，不能交给命名策略自动转换。
        commandInput.set("MainAction", mainAction);
        String packageHash = jsonCodec.sha256(jsonCodec.writeCanonical(commandInput));
        int timeoutMs = actionType == FixedActionType.MOVE
                ? Math.max(template.timeoutMs(), mainAction.at("/phases/0/params/arrival/timeoutMs").asInt() + 5_000)
                : template.timeoutMs();
        return new MaterializedFixedActionPackage(actionType, ACTION_VERSION, template.templateVersion(),
                timeoutMs, commandInput, packageHash);
    }

    private void materializeMove(ObjectNode mainAction, ObjectNode input) {
        String pointName = requiredText(input, "pointName");
        ObjectNode pose = requiredObject(input, "pose");
        requireNumber(pose, "x");
        requireNumber(pose, "y");
        requireNumber(pose, "yaw");
        requiredText(pose, "map");

        ObjectNode parameters = (ObjectNode) mainAction.at("/phases/0/params");
        parameters.put("pointName", pointName);
        parameters.set("pose", pose.deepCopy());
        parameters.put("speed", positiveNumber(input, "speed", 0.5));
        if (input.hasNonNull("port")) {
            parameters.put("port", requiredText(input, "port"));
        } else {
            parameters.remove("port");
        }

        ObjectNode arrival = input.has("arrival") ? requiredObject(input, "arrival").deepCopy()
                : objectMapper.createObjectNode();
        arrival.putIfAbsent("positionToleranceMm", objectMapper.getNodeFactory().numberNode(5));
        arrival.putIfAbsent("angleToleranceDeg", objectMapper.getNodeFactory().numberNode(5));
        arrival.putIfAbsent("timeoutMs", objectMapper.getNodeFactory().numberNode(30_000));
        ensurePositive(arrival, "positionToleranceMm");
        ensurePositive(arrival, "angleToleranceDeg");
        ensurePositive(arrival, "timeoutMs");
        rejectUnknownFields(arrival, ImmutableCollections.setOf("positionToleranceMm", "angleToleranceDeg", "timeoutMs"));
        parameters.set("arrival", arrival);
    }

    private void injectContext(ObjectNode mainAction, ObjectNode input, String defaultStation,
                               String profileField) {
        String station = optionalText(input, "station", defaultStation);
        String point = optionalText(input, "point", null);
        String profile = profileField == null ? null : optionalText(input, profileField,
                profileField.equals("graspProfile") ? "DEFAULT_PICK" : "DEFAULT_PLACE");
        String expectedMaterial = optionalText(input, "expectedMaterial", null);

        for (JsonNode phase : mainAction.withArray("phases")) {
            ObjectNode parameters = (ObjectNode) phase.path("params");
            parameters.put("station", station);
            if (point != null) {
                parameters.put("point", point);
            }
            String subAction = phase.path("subAction").asText();
            if (profileField != null && isProfileAwareSubAction(profileField, subAction)) {
                parameters.put(profileField, profile);
            }
            if (expectedMaterial != null && "VISION.VERIFY_MATERIAL".equals(subAction)) {
                parameters.put("expectedMaterial", expectedMaterial);
            }
        }
    }

    private boolean isProfileAwareSubAction(String profileField, String subAction) {
        if ("graspProfile".equals(profileField)) {
            return ImmutableCollections.setOf("GRIP.OPEN", "GRIP.CLOSE", "GRIP.VERIFY_LOAD").contains(subAction);
        }
        return ImmutableCollections.setOf("GRIP.OPEN", "GRIP.VERIFY_LOAD").contains(subAction);
    }

    private Map<FixedActionType, TemplateDocument> loadTemplates() {
        Map<FixedActionType, TemplateDocument> loaded = new EnumMap<>(FixedActionType.class);
        for (FixedActionType actionType : FixedActionType.values()) {
            String path = RESOURCE_DIRECTORY + actionType.resourceName();
            try (InputStream stream = new ClassPathResource(path).getInputStream()) {
                JsonNode root = objectMapper.readTree(stream);
                String templateVersion = requiredText((ObjectNode) root, "templateVersion");
                int timeoutMs = root.path("timeoutMs").asInt();
                if (timeoutMs <= 0) {
                    throw new IllegalStateException(path + " 的 timeoutMs 必须大于 0");
                }
                ObjectNode mainAction = requiredObject((ObjectNode) root, "MainAction").deepCopy();
                if (!actionType.wireName().equals(mainAction.path("actionType").asText())) {
                    throw new IllegalStateException(path + " 的 actionType 与文件名不匹配");
                }
                loaded.put(actionType, new TemplateDocument(templateVersion, timeoutMs, mainAction));
            } catch (IOException | RuntimeException exception) {
                throw new IllegalStateException("无法加载固定动作模板: " + path, exception);
            }
        }
        return ImmutableCollections.copyMap(loaded);
    }

    private ObjectNode requireObject(JsonNode value) {
        if (value == null || value.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (!value.isObject()) {
            throw new IllegalArgumentException("input 必须是 JSON 对象");
        }
        return (ObjectNode) value;
    }

    private ObjectNode requiredObject(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " 必须是 JSON 对象");
        }
        return (ObjectNode) value;
    }

    private String requiredText(ObjectNode parent, String field) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
            throw new IllegalArgumentException(field + " 必须是非空字符串");
        }
        return value.textValue();
    }

    private String optionalText(ObjectNode parent, String field, String defaultValue) {
        if (!parent.hasNonNull(field)) {
            return defaultValue;
        }
        return requiredText(parent, field);
    }

    private double positiveNumber(ObjectNode parent, String field, double defaultValue) {
        if (!parent.has(field)) {
            return defaultValue;
        }
        ensurePositive(parent, field);
        return parent.get(field).doubleValue();
    }

    private void requireNumber(ObjectNode parent, String field) {
        if (!parent.has(field) || !parent.get(field).isNumber()) {
            throw new IllegalArgumentException(field + " 必须是数值");
        }
    }

    private void ensurePositive(ObjectNode parent, String field) {
        requireNumber(parent, field);
        if (parent.get(field).doubleValue() <= 0) {
            throw new IllegalArgumentException(field + " 必须大于 0");
        }
    }

    private void rejectUnknownFields(FixedActionType type, ObjectNode input) {
        rejectUnknownFields(input, ALLOWED_INPUTS.get(type));
    }

    private void rejectUnknownFields(ObjectNode input, Set<String> allowed) {
        Iterator<String> fieldNames = input.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("不支持的输入参数: " + field);
            }
        }
    }

    @Value
    @Accessors(fluent = true)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class TemplateDocument {
        String templateVersion;
        int timeoutMs;
        ObjectNode mainAction;
        @ConstructorProperties({"templateVersion", "timeoutMs", "mainAction"})
        public TemplateDocument(
                String templateVersion,
                int timeoutMs,
                ObjectNode mainAction
        ) {
            this.templateVersion = templateVersion;
            this.timeoutMs = timeoutMs;
            this.mainAction = mainAction;
        }

    }
}
