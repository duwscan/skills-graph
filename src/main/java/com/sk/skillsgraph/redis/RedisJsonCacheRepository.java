package com.sk.skillsgraph.redis;

import org.springframework.data.repository.CrudRepository;

public interface RedisJsonCacheRepository extends CrudRepository<RedisJsonCacheEntry, String> {
}
