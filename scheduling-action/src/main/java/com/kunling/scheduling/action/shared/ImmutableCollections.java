package com.kunling.scheduling.action.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collector;
import java.util.stream.Collectors;

/**
 * JDK 8 下统一创建只读集合，保留原 JDK 9+ 集合工厂的不可变语义。
 *
 * <p>mapOf 使用键值交替参数，仅用于代码内部的固定字面量；参数数量或类型错误会立即失败，
 * 防止静默生成不完整的协议数据。</p>
 */
public final class ImmutableCollections {

    private ImmutableCollections() {
    }

    @SafeVarargs
    public static <T> List<T> listOf(T... values) {
        if (values == null || values.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(Arrays.asList(values)));
    }

    public static <T> List<T> copyList(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    @SafeVarargs
    public static <T> Set<T> setOf(T... values) {
        if (values == null || values.length == 0) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<T>(Arrays.asList(values)));
    }

    public static <T> Set<T> copySet(Collection<? extends T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<T>(values));
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> mapOf(Object... keyValues) {
        if (keyValues == null || keyValues.length == 0) {
            return Collections.emptyMap();
        }
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("mapOf 参数必须由键值对组成");
        }
        Map<K, V> result = new LinkedHashMap<K, V>();
        for (int index = 0; index < keyValues.length; index += 2) {
            K key = (K) keyValues[index];
            V value = (V) keyValues[index + 1];
            if (key == null || value == null) {
                throw new NullPointerException("只读 Map 不允许 null 键或 null 值");
            }
            if (result.put(key, value) != null) {
                throw new IllegalArgumentException("只读 Map 不允许重复键：" + key);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public static <K, V> Map<K, V> copyMap(Map<? extends K, ? extends V> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<K, V>(values));
    }

    public static <T> Collector<T, ?, List<T>> toImmutableList() {
        return Collectors.collectingAndThen(
                Collectors.<T>toList(),
                values -> ImmutableCollections.copyList(values)
        );
    }
}
