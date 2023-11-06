package com.zero.helper;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * 
 * @say little Boy, don't be sad.
 * @name Rezar
 * @time Aug 18, 2020 7:18:10 PM
 * @Desc 些年若许,不负芳华.
 *
 */
public class SimpleCacheBuilder {

    public static <K, V> LoadingCache<K, V> instance(Function<K, V> load) {
        return com.google.common.cache.CacheBuilder.newBuilder().build(new CacheLoader<K, V>() {
            @Override
            public V load(K key) throws Exception {
                return load.apply(key);
            }
        });
    }

    public static <K, V> LoadingCache<K, V> instance(long expiredAccess, TimeUnit timeUnit, Function<K, V> load) {
        return com.google.common.cache.CacheBuilder.newBuilder().expireAfterAccess(expiredAccess, timeUnit)
                .build(new CacheLoader<K, V>() {
                    @Override
                    public V load(K key) throws Exception {
                        return load.apply(key);
                    }
                });
    }
    
    public static <K, V> LoadingCache<K, V> instanceAfterWrite(long expiredAccess, TimeUnit timeUnit, Function<K, V> load) {
        return com.google.common.cache.CacheBuilder.newBuilder().expireAfterWrite(expiredAccess, timeUnit)
                .build(new CacheLoader<K, V>() {
                    @Override
                    public V load(K key) throws Exception {
                        return load.apply(key);
                    }
                    @Override
                    public Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
                        return super.loadAll(keys);
                    }
                });
    }
    
    public static <K, V> LoadingCache<K, V> instanceAfterWrite(
            long expiredAccess, 
            TimeUnit timeUnit,
            Function<K, V> load,
            Function<Iterable<? extends K>, Map<K, V>> loadAll) {
        return com.google.common.cache.CacheBuilder.newBuilder().expireAfterWrite(expiredAccess, timeUnit)
                .build(new CacheLoader<K, V>() {
                    @Override
                    public V load(K key) throws Exception {
                        return load.apply(key);
                    }
                    @Override
                    public Map<K, V> loadAll(Iterable<? extends K> keys) throws Exception {
                        return loadAll.apply(keys);
                    }
                });
    }

    public static <K, V> Cache<K, V> instance() {
        return com.google.common.cache.CacheBuilder.newBuilder().build();
    }

    public static <K, V> Cache<K, V> instance(long expiredAccess, TimeUnit timeUnit) {
        return com.google.common.cache.CacheBuilder.newBuilder().expireAfterAccess(expiredAccess, timeUnit).build();
    }
    
    public static <K, V> Cache<K, V> instanceAfterWrite(long expiredAccess, TimeUnit timeUnit) {
        return com.google.common.cache.CacheBuilder.newBuilder().expireAfterWrite(expiredAccess, timeUnit).build();
    }

}
