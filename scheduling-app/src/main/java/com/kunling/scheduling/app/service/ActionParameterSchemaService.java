package com.kunling.scheduling.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kunling.scheduling.app.domain.ActionParameterSchema;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ActionParameterField;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ParameterDataType;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ParameterOwnerType;
import com.kunling.scheduling.app.domain.ActionParameterSchema.SaveRequest;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ValidationIssue;
import com.kunling.scheduling.app.domain.ActionParameterSchema.ValidationResult;
import com.kunling.scheduling.app.mapper.ActionParameterSchemaMapper;
import com.kunling.scheduling.common.exception.InvalidRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Action 参数 Schema 的唯一业务入口。
 *
 * <p>本功能数据结构简单，Service 统一处理 JSON 转换、upsert 与校验规则，
 * Mapper 只负责 SQL，不再增加无业务逻辑的 Repository 转发层。</p>
 */
@Service
public class ActionParameterSchemaService {

    private static final int OWNER_KEY_MAX_LENGTH = 128;
    private static final Pattern PARAMETER_KEY_PATTERN =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    private final ActionParameterSchemaMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public ActionParameterSchemaService(ActionParameterSchemaMapper mapper,
                                        ObjectMapper objectMapper) {
        this(mapper, objectMapper, Clock.systemUTC());
    }

    ActionParameterSchemaService(ActionParameterSchemaMapper mapper,
                                 ObjectMapper objectMapper,
                                 Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** 未配置时返回空字段集合，不把“无配置”视为资源不存在。 */
    @Transactional(readOnly = true)
    public ActionParameterSchema get(ParameterOwnerType ownerType, String ownerKey) {
        Owner owner = requireOwner(ownerType, ownerKey);
        String schemaJson = mapper.findSchemaJson(owner.type.name(), owner.key);
        if (schemaJson == null) {
            return new ActionParameterSchema(owner.type, owner.key, Collections.emptyList());
        }
        return new ActionParameterSchema(owner.type, owner.key,
                sortedFields(readFields(schemaJson)));
    }

    /** 完整覆盖保存；fields=[] 是合法配置，fields 缺失则拒绝以防误清空。 */
    @Transactional
    public ActionParameterSchema save(ParameterOwnerType ownerType,
                                      String ownerKey,
                                      SaveRequest request) {
        Owner owner = requireOwner(ownerType, ownerKey);
        if (request == null || request.getFields() == null) {
            throw new InvalidRequestException("fields 必须显式提交；如无动态参数请提交空数组");
        }

        ActionParameterSchema draft = new ActionParameterSchema(
                owner.type, owner.key, request.getFields());
        List<ValidationIssue> issues = validateSchema(draft);
        if (!issues.isEmpty()) {
            ValidationIssue first = issues.get(0);
            throw new InvalidRequestException("参数 Schema 校验失败：" + first.getPath()
                    + " " + first.getMessage() + (issues.size() > 1
                    ? "（共 " + issues.size() + " 项）" : ""));
        }

        ActionParameterSchema normalized = new ActionParameterSchema(
                owner.type, owner.key, sortedFields(draft.getFields()));
        saveSchema(normalized);
        return normalized;
    }

    /** 按已保存 Schema 校验一次实际参数值，不做任何类型隐式转换。 */
    @Transactional(readOnly = true)
    public ValidationResult validate(ParameterOwnerType ownerType,
                                     String ownerKey,
                                     JsonNode parameterValues) {
        ActionParameterSchema schema = get(ownerType, ownerKey);
        return new ValidationResult(validateValues(schema, parameterValues));
    }

    private void saveSchema(ActionParameterSchema schema) {
        String ownerType = schema.getOwnerType().name();
        String ownerKey = schema.getOwnerKey();
        String schemaJson = writeFields(schema.getFields());
        Timestamp savedAt = Timestamp.from(clock.instant());
        if (mapper.update(ownerType, ownerKey, schemaJson, savedAt) > 0) {
            return;
        }
        try {
            mapper.insert(UUID.randomUUID().toString(), ownerType, ownerKey,
                    schemaJson, savedAt, savedAt);
        } catch (DuplicateKeyException concurrentCreate) {
            // 唯一键兜住并发首次新增，再覆盖一次即可保持 PUT 语义。
            if (mapper.update(ownerType, ownerKey, schemaJson, savedAt) == 0) {
                throw concurrentCreate;
            }
        }
    }

    private List<ValidationIssue> validateSchema(ActionParameterSchema schema) {
        List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
        Set<String> keys = new HashSet<String>();
        Set<Integer> sorts = new HashSet<Integer>();
        List<ActionParameterField> fields = schema.getFields();

        for (int index = 0; index < fields.size(); index++) {
            ActionParameterField field = fields.get(index);
            String basePath = "$.fields[" + index + "]";
            if (field == null) {
                issues.add(issue(basePath, "TYPE_MISMATCH", "参数字段不能为 null"));
                continue;
            }
            validateFieldIdentity(field, basePath, keys, sorts, issues);
            validateFieldConstraints(field, basePath, issues);
        }
        return issues;
    }

    private void validateFieldIdentity(ActionParameterField field,
                                       String basePath,
                                       Set<String> keys,
                                       Set<Integer> sorts,
                                       List<ValidationIssue> issues) {
        if (field.getKey() == null || !PARAMETER_KEY_PATTERN.matcher(field.getKey()).matches()) {
            issues.add(issue(basePath + ".key", "INVALID_KEY",
                    "参数键必须以字母开头，且只能包含字母、数字和下划线，最长 64 位"));
        } else if (!keys.add(field.getKey())) {
            issues.add(issue(basePath + ".key", "DUPLICATE_KEY",
                    "参数键不能重复：" + field.getKey()));
        }

        if (!hasText(field.getLabel())) {
            issues.add(issue(basePath + ".label", "REQUIRED", "显示名称不能为空"));
        }
        if (field.getDataType() == null) {
            issues.add(issue(basePath + ".dataType", "REQUIRED", "数据类型不能为空"));
        }
        if (field.getSort() <= 0) {
            issues.add(issue(basePath + ".sort", "REQUIRED", "排序必须为正整数"));
        } else if (!sorts.add(field.getSort())) {
            issues.add(issue(basePath + ".sort", "DUPLICATE_SORT",
                    "排序值不能重复：" + field.getSort()));
        }
    }

    private void validateFieldConstraints(ActionParameterField field,
                                          String basePath,
                                          List<ValidationIssue> issues) {
        ParameterDataType dataType = field.getDataType();
        if (dataType == null) {
            return;
        }

        boolean numeric = dataType == ParameterDataType.INTEGER
                || dataType == ParameterDataType.DECIMAL;
        if (!numeric && (field.getMinimum() != null || field.getMaximum() != null)) {
            issues.add(issue(basePath, "INVALID_RANGE", "只有数值类型可以配置最小值和最大值"));
        }
        if (field.getMinimum() != null && field.getMaximum() != null
                && field.getMinimum().compareTo(field.getMaximum()) > 0) {
            issues.add(issue(basePath, "INVALID_RANGE", "最小值不能大于最大值"));
        }

        if (dataType == ParameterDataType.ENUM) {
            validateEnumOptions(field, basePath, issues);
        } else if (!field.getEnumValues().isEmpty()) {
            issues.add(issue(basePath + ".enumValues", "INVALID_ENUM",
                    "只有 ENUM 类型可以配置枚举选项"));
        }

        JsonNode defaultValue = field.getDefaultValue();
        if (defaultValue == null) {
            return;
        }
        if (!matchesType(dataType, defaultValue)) {
            issues.add(issue(basePath + ".defaultValue", "TYPE_MISMATCH",
                    "默认值与 " + dataType + " 类型不匹配"));
            return;
        }
        if (numeric) {
            validateNumberRange(defaultValue.decimalValue(), field, basePath + ".defaultValue", issues);
        }
        if (dataType == ParameterDataType.ENUM
                && !field.getEnumValues().contains(defaultValue.textValue())) {
            issues.add(issue(basePath + ".defaultValue", "INVALID_ENUM",
                    "枚举默认值必须来自 enumValues"));
        }
    }

    private void validateEnumOptions(ActionParameterField field,
                                     String basePath,
                                     List<ValidationIssue> issues) {
        List<String> values = field.getEnumValues();
        if (values.isEmpty()) {
            issues.add(issue(basePath + ".enumValues", "INVALID_ENUM",
                    "ENUM 类型至少需要一个可选项"));
            return;
        }
        Set<String> distinct = new HashSet<String>();
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (!hasText(value)) {
                issues.add(issue(basePath + ".enumValues[" + index + "]", "INVALID_ENUM",
                        "枚举选项不能为空"));
            } else if (!distinct.add(value)) {
                issues.add(issue(basePath + ".enumValues[" + index + "]", "INVALID_ENUM",
                        "枚举选项不能重复：" + value));
            }
        }
    }

    private List<ValidationIssue> validateValues(ActionParameterSchema schema,
                                                 JsonNode parameterValues) {
        List<ValidationIssue> issues = new ArrayList<ValidationIssue>();
        if (parameterValues == null || !parameterValues.isObject()) {
            issues.add(issue("$", "TYPE_MISMATCH", "参数值根节点必须是 JSON 对象"));
            return issues;
        }

        Map<String, ActionParameterField> declaredFields = new LinkedHashMap<String, ActionParameterField>();
        for (ActionParameterField field : schema.getFields()) {
            declaredFields.put(field.getKey(), field);
            JsonNode value = parameterValues.get(field.getKey());
            String path = jsonPath(field.getKey());
            if (value == null || value.isNull()) {
                if (field.isRequired()) {
                    issues.add(issue(path, "REQUIRED", displayName(field) + "不能为空"));
                }
                continue;
            }
            if (!matchesType(field.getDataType(), value)) {
                issues.add(issue(path, "TYPE_MISMATCH",
                        displayName(field) + "必须是 " + field.getDataType() + " 类型"));
                continue;
            }
            if (field.getDataType() == ParameterDataType.INTEGER
                    || field.getDataType() == ParameterDataType.DECIMAL) {
                validateNumberRange(value.decimalValue(), field, path, issues);
            }
            if (field.getDataType() == ParameterDataType.ENUM
                    && !field.getEnumValues().contains(value.textValue())) {
                issues.add(issue(path, "INVALID_ENUM",
                        displayName(field) + "必须是允许的枚举值"));
            }
        }

        List<String> unknownKeys = new ArrayList<String>();
        Iterator<String> names = parameterValues.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!declaredFields.containsKey(name)) {
                unknownKeys.add(name);
            }
        }
        Collections.sort(unknownKeys);
        for (String unknownKey : unknownKeys) {
            issues.add(issue(jsonPath(unknownKey), "UNKNOWN_FIELD",
                    "Schema 未声明参数：" + unknownKey));
        }
        return issues;
    }

    private void validateNumberRange(BigDecimal value,
                                     ActionParameterField field,
                                     String path,
                                     List<ValidationIssue> issues) {
        if (field.getMinimum() != null && value.compareTo(field.getMinimum()) < 0) {
            issues.add(issue(path, "OUT_OF_RANGE",
                    displayName(field) + "不能小于 " + field.getMinimum().toPlainString()));
        }
        if (field.getMaximum() != null && value.compareTo(field.getMaximum()) > 0) {
            issues.add(issue(path, "OUT_OF_RANGE",
                    displayName(field) + "不能大于 " + field.getMaximum().toPlainString()));
        }
    }

    private boolean matchesType(ParameterDataType dataType, JsonNode value) {
        if (dataType == null || value == null || value.isNull()) {
            return false;
        }
        switch (dataType) {
            case STRING:
            case ENUM:
                return value.isTextual();
            case INTEGER:
                return value.isIntegralNumber();
            case DECIMAL:
                return value.isNumber();
            case BOOLEAN:
                return value.isBoolean();
            case ARRAY:
                return value.isArray();
            case OBJECT:
                return value.isObject();
            default:
                return false;
        }
    }

    private Owner requireOwner(ParameterOwnerType ownerType, String ownerKey) {
        if (ownerType == null) {
            throw new InvalidRequestException("ownerType 不能为空");
        }
        String normalizedKey = ownerKey == null ? null : ownerKey.trim();
        if (!hasText(normalizedKey)) {
            throw new InvalidRequestException("ownerKey 不能为空");
        }
        if (normalizedKey.length() > OWNER_KEY_MAX_LENGTH) {
            throw new InvalidRequestException("ownerKey 长度不能超过 " + OWNER_KEY_MAX_LENGTH + " 位");
        }
        return new Owner(ownerType, normalizedKey);
    }

    private List<ActionParameterField> sortedFields(List<ActionParameterField> fields) {
        List<ActionParameterField> sorted = new ArrayList<ActionParameterField>(fields);
        sorted.sort(Comparator.comparingInt(ActionParameterField::getSort));
        return sorted;
    }

    private String writeFields(List<ActionParameterField> fields) {
        ObjectNode root = objectMapper.createObjectNode();
        root.set("fields", objectMapper.valueToTree(fields));
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Action 参数 Schema 无法序列化", exception);
        }
    }

    private List<ActionParameterField> readFields(String schemaJson) {
        try {
            JsonNode root = objectMapper.readTree(schemaJson);
            JsonNode fieldsNode = root == null ? null : root.get("fields");
            if (root == null || !root.isObject() || fieldsNode == null || !fieldsNode.isArray()) {
                throw new IllegalStateException("持久化的 Action 参数 Schema 结构无效");
            }
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ActionParameterField.class);
            return objectMapper.convertValue(fieldsNode, listType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new IllegalStateException("持久化的 Action 参数 Schema 无法解析", exception);
        }
    }

    private String displayName(ActionParameterField field) {
        return hasText(field.getLabel()) ? field.getLabel() : field.getKey();
    }

    private String jsonPath(String key) {
        if (key != null && PARAMETER_KEY_PATTERN.matcher(key).matches()) {
            return "$." + key;
        }
        String escaped = key == null ? "null"
                : key.replace("\\", "\\\\").replace("'", "\\'");
        return "$['" + escaped + "']";
    }

    private ValidationIssue issue(String path, String code, String message) {
        return new ValidationIssue(path, code, message);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /** 归属值对象只在 Service 内使用，避免额外增加无业务价值的类型文件。 */
    private static final class Owner {
        private final ParameterOwnerType type;
        private final String key;

        private Owner(ParameterOwnerType type, String key) {
            this.type = type;
            this.key = key;
        }
    }
}
