package com.kunling.scheduling.app.service.aspect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.kunling.scheduling.common.audit.OperationLog;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将请求和响应转换为经过脱敏、限长的 JSON 文本。 */
@Component
public class OperationLogPayloadSerializer {

    public static final int CONTENT_LIMIT = 4000;
    public static final int ERROR_LIMIT = 2000;
    private static final String MASK = "***";
    private static final Logger LOGGER = LoggerFactory.getLogger(OperationLogPayloadSerializer.class);
    private static final Set<String> DEFAULT_SENSITIVE_FIELDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "password", "oldpassword", "newpassword", "confirmpassword", "token", "accesstoken",
            "refreshtoken", "authorization", "secret", "apikey", "userid", "username",
            "operatorname", "opername", "phone", "phonenumber", "mobile", "email", "assignee")));

    private final ObjectMapper objectMapper;

    public OperationLogPayloadSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serializeRequest(ProceedingJoinPoint joinPoint, OperationLog operationLog,
                                   HttpServletRequest request) {
        if (!operationLog.recordRequest()) {
            return null;
        }
        try {
            Set<String> sensitiveFields = sensitiveFields(operationLog);
            ObjectNode root = objectMapper.createObjectNode();
            root.set("path", sanitize(objectMapper.valueToTree(pathVariables(request)), sensitiveFields));
            root.set("query", sanitize(objectMapper.valueToTree(request.getParameterMap()), sensitiveFields));
            root.set("body", sanitize(bodyNode(joinPoint), sensitiveFields));
            return truncate(objectMapper.writeValueAsString(root), CONTENT_LIMIT);
        } catch (Exception exception) {
            LOGGER.warn("操作日志请求参数序列化失败: {}",
                    joinPoint.getSignature().toShortString(), exception);
            return null;
        }
    }

    public String serializeResponse(Object response, OperationLog operationLog) {
        if (!operationLog.recordResponse() || response == null) {
            return null;
        }
        try {
            JsonNode node = sanitize(objectMapper.valueToTree(response), sensitiveFields(operationLog));
            return truncate(objectMapper.writeValueAsString(node), CONTENT_LIMIT);
        } catch (Exception exception) {
            LOGGER.warn("操作日志响应内容序列化失败", exception);
            return null;
        }
    }

    public String truncateError(String message) {
        return truncate(message, ERROR_LIMIT);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> pathVariables(HttpServletRequest request) {
        Object value = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    private JsonNode bodyNode(ProceedingJoinPoint joinPoint) {
        ArrayNode bodies = objectMapper.createArrayNode();
        Object[] arguments = joinPoint.getArgs();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = AopUtils.getMostSpecificMethod(
                signature.getMethod(), joinPoint.getTarget().getClass());
        Annotation[][] annotations = targetMethod.getParameterAnnotations();
        for (int index = 0; index < arguments.length; index++) {
            if (hasRequestBody(annotations[index]) && isSerializableBody(arguments[index])) {
                bodies.add(objectMapper.valueToTree(arguments[index]));
            }
        }
        return bodies.size() == 1 ? bodies.get(0) : bodies;
    }

    private boolean hasRequestBody(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == RequestBody.class) {
                return true;
            }
        }
        return false;
    }

    private boolean isSerializableBody(Object value) {
        return value != null
                && !(value instanceof ServletRequest)
                && !(value instanceof ServletResponse)
                && !(value instanceof MultipartFile)
                && !(value instanceof MultipartFile[])
                && !(value instanceof Principal);
    }

    private Set<String> sensitiveFields(OperationLog operationLog) {
        Set<String> fields = new HashSet<>(DEFAULT_SENSITIVE_FIELDS);
        for (String field : operationLog.sensitiveFields()) {
            fields.add(normalizeField(field));
        }
        return fields;
    }

    private JsonNode sanitize(JsonNode node, Set<String> sensitiveFields) {
        if (node == null || node.isNull()) {
            return node;
        }
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveField(field.getKey(), sensitiveFields)) {
                    objectNode.set(field.getKey(), TextNode.valueOf(MASK));
                } else {
                    sanitize(field.getValue(), sensitiveFields);
                }
            }
        } else if (node.isArray()) {
            node.forEach(item -> sanitize(item, sensitiveFields));
        }
        return node;
    }

    private boolean isSensitiveField(String field, Set<String> sensitiveFields) {
        String normalized = normalizeField(field);
        return sensitiveFields.contains(normalized)
                || normalized.startsWith("password") || normalized.endsWith("password")
                || normalized.startsWith("token") || normalized.endsWith("token")
                || normalized.startsWith("secret") || normalized.endsWith("secret")
                || normalized.startsWith("authorization") || normalized.endsWith("authorization")
                || normalized.startsWith("apikey") || normalized.endsWith("apikey");
    }

    private String normalizeField(String field) {
        return field == null ? "" : field.replace("_", "").replace("-", "")
                .replace(" ", "").toLowerCase(Locale.ROOT);
    }

    private String truncate(String value, int limit) {
        return value == null || value.length() <= limit ? value : value.substring(0, limit);
    }
}
