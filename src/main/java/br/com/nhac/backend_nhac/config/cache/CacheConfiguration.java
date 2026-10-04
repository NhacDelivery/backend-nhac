package br.com.nhac.backend_nhac.config.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

import static br.com.nhac.backend_nhac.config.cache.CacheNames.*;

@Configuration(proxyBeanMethods = false)
@EnableCaching
public class CacheConfiguration {
    @Bean
    public CacheManager cacheManager(
            @Value("${nhac.cache.enabled:true}") boolean enabled,
            @Value("${nhac.cache.catalog-ttl:30s}") Duration catalogTtl,
            @Value("${nhac.cache.ratings-ttl:2m}") Duration ratingsTtl,
            @Value("${nhac.cache.list-maximum-size:200}") long listMaximumSize,
            @Value("${nhac.cache.detail-maximum-size:2000}") long detailMaximumSize) {
        if (!enabled) {
            return new NoOpCacheManager();
        }
        var manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                cache(LOJAS, catalogTtl, listMaximumSize),
                cache(LOJA, catalogTtl, detailMaximumSize),
                cache(PRODUTOS, catalogTtl, listMaximumSize),
                cache(PRODUTO, catalogTtl, detailMaximumSize),
                cache(PRODUTO_AVALIACOES, ratingsTtl, detailMaximumSize),
                cache(ENTREGADOR_AVALIACOES, ratingsTtl, detailMaximumSize)));
        manager.initializeCaches();
        // Puts and invalidation run only after a successful transaction commit.
        // Do not use sync=true or beforeInvocation=true: those bypass deferred operations.
        return new TransactionAwareCacheManagerProxy(manager);
    }

    private Cache cache(String name, Duration ttl, long maximumSize) {
        if (ttl.isZero() || ttl.isNegative() || maximumSize <= 0) {
            throw new IllegalArgumentException("TTL e tamanho do cache devem ser positivos: " + name);
        }
        return new CaffeineCache(name, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maximumSize)
                .recordStats()
                .build(), false);
    }
}
