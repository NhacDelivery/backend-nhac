package br.com.nhac.backend_nhac.config.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static br.com.nhac.backend_nhac.config.cache.CacheNames.*;
import static org.junit.jupiter.api.Assertions.*;

class CacheConfigurationTest {
    @Test
    void allCachesHaveBoundsExpiryAndStatistics() {
        var manager = new CacheConfiguration().cacheManager(true, Duration.ofSeconds(30), Duration.ofMinutes(2), 2, 3);
        assertEquals(Set.of(LOJAS, LOJA, PRODUTOS, PRODUTO, PRODUTO_AVALIACOES, ENTREGADOR_AVALIACOES), Set.copyOf(manager.getCacheNames()));
        assertNull(manager.getCache("typo"));
        for (String name : manager.getCacheNames()) {
            var springCache = manager.getCache(name);
            var nativeCache = (Cache<Object, Object>) springCache.getNativeCache();
            boolean list = name.equals(LOJAS) || name.equals(PRODUTOS);
            assertEquals(list ? 2 : 3, nativeCache.policy().eviction().orElseThrow().getMaximum());
            assertEquals((name.equals(PRODUTO_AVALIACOES) || name.equals(ENTREGADOR_AVALIACOES)) ? Duration.ofMinutes(2) : Duration.ofSeconds(30),
                    nativeCache.policy().expireAfterWrite().orElseThrow().getExpiresAfter());
            for (int i = 0; i < 10; i++) springCache.put(i, "value");
            nativeCache.cleanUp();
            assertTrue(nativeCache.estimatedSize() <= (list ? 2 : 3));
            springCache.get("missing");
            assertTrue(nativeCache.stats().missCount() > 0);
            // Changing the policy makes expiration deterministic without sleeping.
            nativeCache.policy().expireAfterWrite().orElseThrow().setExpiresAfter(Duration.ZERO);
            assertNull(springCache.get(9));
        }
    }

    @Test
    void configurationCanDisableCaching() {
        var manager = new CacheConfiguration().cacheManager(false, Duration.ofSeconds(30), Duration.ofMinutes(2), 2, 3);
        manager.getCache(PRODUTO).put("id", "value");
        assertNull(manager.getCache(PRODUTO).get("id"));
    }

    @Test
    void invalidLimitsFailAtStartup() {
        var config = new CacheConfiguration();
        assertThrows(IllegalArgumentException.class, () -> config.cacheManager(true, Duration.ZERO, Duration.ofMinutes(2), 2, 3));
        assertThrows(IllegalArgumentException.class, () -> config.cacheManager(true, Duration.ofSeconds(30), Duration.ofMinutes(2), 0, 3));
    }
}
