package com.kunling.scheduling.action.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JsonCodec {

    private final ObjectMapper objectMapper;
    private final ObjectMapper canonicalMapper;

    public JsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        ObjectMapper canonicalObjectMapper = objectMapper.copy();
        // 通过不可变配置副本启用稳定排序，避免使用 Jackson 已废弃的可变 MapperFeature 接口。
        canonicalObjectMapper.setConfig(canonicalObjectMapper.getSerializationConfig()
                .with(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS));
        this.canonicalMapper = canonicalObjectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("对象无法序列化为 JSON", exception);
        }
    }

    public String writeCanonical(Object value) {
        try {
            // JsonNode 会保留插入顺序，单靠 ORDER_MAP_ENTRIES_BY_KEYS 不能保证对象节点稳定排序。
            // 先转为树并递归排序，确保同一动作包不因请求字段顺序不同而产生不同 Hash。
            return canonicalMapper.writeValueAsString(sortTree(objectMapper.valueToTree(value)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("对象无法生成规范 JSON", exception);
        }
    }

    private JsonNode sortTree(JsonNode value) {
        if (value.isObject()) {
            ObjectNode sorted = canonicalMapper.createObjectNode();
            List<Map.Entry<String, JsonNode>> fields = new ArrayList<Map.Entry<String, JsonNode>>();
            value.fields().forEachRemaining(fields::add);
            fields.sort(Map.Entry.comparingByKey());
            fields.forEach(entry -> sorted.set(entry.getKey(), sortTree(entry.getValue())));
            return sorted;
        }
        if (value.isArray()) {
            ArrayNode sorted = canonicalMapper.createArrayNode();
            value.forEach(item -> sorted.add(sortTree(item)));
            return sorted;
        }
        return value.deepCopy();
    }

    public <T> T read(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("持久化 JSON 无法反序列化为 " + type.getSimpleName(), exception);
        }
    }

    public JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("持久化 JSON 无法解析", exception);
        }
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte valueByte : digest) {
                hex.append(String.format("%02x", valueByte & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }
}
