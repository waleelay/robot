package com.robot.bigscreen.panorama;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoundedTtlCacheTest {

    @Test
    void removesOneEntryWithoutAffectingOthers() {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(2, 1000);
        cache.put("a", "A");
        cache.put("b", "B");

        cache.remove("a");

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.get("b")).contains("B");
    }

    @Test
    void evictsLeastRecentlyUsedEntryAtCapacity() {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(2, 1000);
        cache.put("a", "A");
        cache.put("b", "B");
        assertThat(cache.get("a")).contains("A");

        cache.put("c", "C");

        assertThat(cache.get("b")).isEmpty();
        assertThat(cache.get("a")).contains("A");
        assertThat(cache.get("c")).contains("C");
    }

    @Test
    void removesExpiredEntries() throws Exception {
        BoundedTtlCache<String, String> cache = new BoundedTtlCache<>(2, 10);
        cache.put("a", "A");

        Thread.sleep(20);

        assertThat(cache.get("a")).isEmpty();
        assertThat(cache.size()).isZero();
    }
}
