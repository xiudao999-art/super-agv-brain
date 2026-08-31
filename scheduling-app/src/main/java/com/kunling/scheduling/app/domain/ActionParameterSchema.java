package com.kunling.scheduling.app.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 主 Action 或子 Action 的完整入参结构。
 *
 * <p>为控制本功能的类型数量，请求、字段和校验结果作为内嵌模型统一维护；
 * 对外 JSON 仍保持扁平、稳定，不暴露数据库结构。</p>
 */
@Schema(description = "Action 动态入参 Schema")
public final class ActionParameterSchema {

    private final ParameterOwnerType ownerType;
    private final String ownerKey;
    private final List<ActionParameterField> fields;

    public ActionParameterSchema(ParameterOwnerType ownerType,
                                 String ownerKey,
                                 List<ActionParameterField> fields) {
        this.ownerType = ownerType;
        this.ownerKey = ownerKey;
        this.fields = immutableCopy(fields);
    }

    public ParameterOwnerType getOwnerType() {
        return ownerType;
    }

    public String getOwnerKey() {
        return ownerKey;
    }

    public List<ActionParameterField> getFields() {
        return fields;
    }

    /** 参数配置归属。 */
    public enum ParameterOwnerType {
        MAIN_ACTION,
        SUB_ACTION
    }

    /** 一期支持的最小数据类型集合。 */
    public enum ParameterDataType {
        STRING,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        ENUM,
        ARRAY,
        OBJECT
    }

    /** 单个动态参数字段及其基础约束。 */
    @Schema(description = "Action 动态参数字段")
    public static final class ActionParameterField {
        private final String key;
        private final String label;
        private final ParameterDataType dataType;
        private final boolean required;
        private final JsonNode defaultValue;
        private final String unit;
        private final String description;
        private final BigDecimal minimum;
        private final BigDecimal maximum;
        private final List<String> enumValues;
        private final int sort;

        @JsonCreator
        public ActionParameterField(@JsonProperty("key") String key,
                                    @JsonProperty("label") String label,
                                    @JsonProperty("dataType") ParameterDataType dataType,
                                    @JsonProperty("required") boolean required,
                                    @JsonProperty("defaultValue") JsonNode defaultValue,
                                    @JsonProperty("unit") String unit,
                                    @JsonProperty("description") String description,
                                    @JsonProperty("minimum") BigDecimal minimum,
                                    @JsonProperty("maximum") BigDecimal maximum,
                                    @JsonProperty("enumValues") List<String> enumValues,
                                    @JsonProperty("sort") int sort) {
            this.key = normalizeText(key);
            this.label = normalizeText(label);
            this.dataType = dataType;
            this.required = required;
            this.defaultValue = defaultValue == null || defaultValue.isNull()
                    ? null : defaultValue.deepCopy();
            this.unit = normalizeOptionalText(unit);
            this.description = normalizeOptionalText(description);
            this.minimum = minimum;
            this.maximum = maximum;
            this.enumValues = immutableStrings(enumValues);
            this.sort = sort;
        }

        public String getKey() { return key; }
        public String getLabel() { return label; }
        public ParameterDataType getDataType() { return dataType; }
        public boolean isRequired() { return required; }
        public JsonNode getDefaultValue() {
            return defaultValue == null ? null : defaultValue.deepCopy();
        }
        public String getUnit() { return unit; }
        public String getDescription() { return description; }
        public BigDecimal getMinimum() { return minimum; }
        public BigDecimal getMaximum() { return maximum; }
        public List<String> getEnumValues() { return enumValues; }
        public int getSort() { return sort; }
    }

    /** 覆盖保存请求；fields 必须显式提交，空数组表示清空动态参数。 */
    public static final class SaveRequest {
        private final List<ActionParameterField> fields;

        @JsonCreator
        public SaveRequest(@JsonProperty("fields") List<ActionParameterField> fields) {
            this.fields = fields == null ? null : immutableCopy(fields);
        }

        public List<ActionParameterField> getFields() {
            return fields;
        }
    }

    /** 一个可定位到 JSON 路径的校验问题。 */
    public static final class ValidationIssue {
        private final String path;
        private final String code;
        private final String message;

        public ValidationIssue(String path, String code, String message) {
            this.path = path;
            this.code = code;
            this.message = message;
        }

        public String getPath() { return path; }
        public String getCode() { return code; }
        public String getMessage() { return message; }
    }

    /** 参数值校验结果；校验失败仍作为正常业务结果返回。 */
    public static final class ValidationResult {
        private final boolean valid;
        private final List<ValidationIssue> issues;

        public ValidationResult(List<ValidationIssue> issues) {
            this.issues = immutableCopy(issues);
            this.valid = this.issues.isEmpty();
        }

        public boolean isValid() { return valid; }
        public List<ValidationIssue> getIssues() { return issues; }
    }

    private static String normalizeText(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeOptionalText(String value) {
        String normalized = normalizeText(value);
        return normalized == null || normalized.isEmpty() ? null : normalized;
    }

    private static List<String> immutableStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> copy = new ArrayList<String>(values.size());
        for (String value : values) {
            copy.add(normalizeText(value));
        }
        return Collections.unmodifiableList(copy);
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
