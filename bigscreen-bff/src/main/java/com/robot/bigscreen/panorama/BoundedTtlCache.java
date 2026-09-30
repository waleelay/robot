package com.robot.bigscreen.panorama;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 单实例短时缓存。容量和有效期在一个实现中收口，避免各业务缓存分别维护清理锁。
 */
final class BoundedTtlCache<K, V> {

    /**
     * 缓存条目数上限，超出后按最近访问顺序淘汰。
     */
    private final int maximumSize;
    /**
     * 条目写入后的有效时长，单位毫秒；读取不延长有效期。
     */
    private final long ttlMillis;
    /**
     * 按访问顺序保存缓存项；所有访问均由当前缓存对象的监视锁保护。
     */
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

    /**
     * 写入条目并设置绝对到期时间，清除过期项后按最近访问顺序淘汰超额条目。
     */
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

    synchronized void removeIf(Predicate<K> predicate) {
        entries.keySet().removeIf(predicate);
    }

    private void removeExpired(long now) {
        entries.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    /**
     * 带失效时间的缓存项，过期后不再用于请求复用。
     * @param value 缓存或当前对象保存的值
     * @param expiresAt 缓存到期的系统时间戳，单位毫秒
     */
    private record Entry<V>(V value, long expiresAt) {
    }
}
