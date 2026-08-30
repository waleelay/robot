package com.robot.bigscreen.panorama;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 单实例短时缓存。容量和有效期在一个实现中收口，避免各业务缓存分别维护清理锁。
 */
final class BoundedTtlCache<K, V> {

    private final int maximumSize;
    private final long ttlMillis;
    private final LinkedHashMap<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    BoundedTtlCache(int maximumSize, long ttlMillis) {
        if (maximumSize <= 0 || ttlMillis <= 0) {
            throw new IllegalArgumentException("缓存容量和有效期必须大于零");
        }
        this.maximumSize = maximumSize;
        this.ttlMillis = ttlMillis;
    }

    synchronized Optional<V> get(K key) {
        long now = System.currentTimeMillis();
        removeExpired(now);
        Entry<V> entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    synchronized void put(K key, V value) {
        long now = System.currentTimeMillis();
        removeExpired(now);
        entries.put(key, new Entry<>(value, now + ttlMillis));
        while (entries.size() > maximumSize) {
            Iterator<K> iterator = entries.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    synchronized int size() {
        removeExpired(System.currentTimeMillis());
        return entries.size();
    }

    synchronized void remove(K key) {
        entries.remove(key);
    }

    private void removeExpired(long now) {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private record Entry<V>(V value, long expiresAt) {
    }
}
