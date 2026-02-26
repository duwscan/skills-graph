package com.sk.skillsgraph.redis;

import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.TimeToLive;

@RedisHash("cache")
public class RedisJsonCacheEntry {

    @Id
    private String id;

    private String payload;

    @TimeToLive
    private Long ttlSeconds;

    public RedisJsonCacheEntry() {
    }

    public RedisJsonCacheEntry(String id, String payload, Long ttlSeconds) {
        this.id = id;
        this.payload = payload;
        this.ttlSeconds = ttlSeconds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
