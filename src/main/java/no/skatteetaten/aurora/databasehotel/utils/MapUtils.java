package no.skatteetaten.aurora.databasehotel.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.Sets;

public class MapUtils {

    @SafeVarargs
    public static <K> boolean containsEveryKey(Map<K, ?> map, K... keys) {

        Set<K> keySet = Sets.newHashSet(keys);
        return map.keySet().containsAll(keySet);
    }

    public static <K, V, Z extends V> KeyValue<K, V> kv(K key, Z value) {

        return new KeyValue<>(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T, K> T get(Map<K, ?> map, K key) {

        if (map == null) {
            throw new IllegalArgumentException(String.format("Cannot get value for key [%s] on null map", key));
        }
        return (T) map.get(key);
    }

    public static <K> Boolean getAsBoolean(Map<K, ?> map, K key) {

        if (map == null) {
            throw new IllegalArgumentException(String.format("Cannot get value for key [%s] on null map", key));
        }
        Object o = map.get(key);
        if (o == null) {
            return false;
        }
        return Boolean.valueOf(o.toString());
    }

    public static <T, K extends Object> Optional<T> maybeGet(Map<K, ?> map, K key) {

        T object = get(map, key);
        return Optional.ofNullable(object);
    }

    public static <K extends Object> Optional<Boolean> maybeGetAsBoolean(Map<K, ?> map, K key) {

        Boolean b = getAsBoolean(map, key);
        return Optional.ofNullable(b);
    }

    @SafeVarargs
    public static <K, V, Z extends V> void put(Map<K, V> map, KeyValue<K, Z>... kv) {
        for (KeyValue<K, Z> keyValue : kv) {
            map.put(keyValue.key, keyValue.value);
        }
    }

    @SafeVarargs
    public static <K, V, Z extends V> Map<K, V> from(KeyValue<K, Z>... kv) {
        Map<K, V> map = new HashMap<>();
        put(map, kv);
        return map;
    }

    public static <T, K> Map<K, T> createUniqueIndex(Collection<T> objects, Function<T, K> keyGenerator) {

        if (objects == null) {
            throw new IllegalArgumentException("Cannot generate index from null collection");
        }
        Map<K, T> index = new HashMap<>();
        objects.forEach(obj -> index.put(keyGenerator.apply(obj), obj));
        return index;
    }

    public static <T, K> Map<K, List<T>> createNonUniqueIndex(Collection<T> objects, Function<T, K> keyGenerator) {

        if (objects == null) {
            throw new IllegalArgumentException("Cannot generate index from null collection");
        }
        Map<K, List<T>> index = new HashMap<>();
        objects.forEach(obj -> {
            K key = keyGenerator.apply(obj);
            List<T> instances = index.computeIfAbsent(key, k -> new ArrayList<>());
            instances.add(obj);
        });
        return index;
    }

    public static class KeyValue<K, V> {
        final K key;

        final V value;

        protected KeyValue(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
