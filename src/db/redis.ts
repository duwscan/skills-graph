import Redis from "ioredis";
import { config } from "../config/env";
import { REDIS_CONNECT_TIMEOUT_MS } from "../config/constants";

export const redis = new Redis(config.REDIS_URL, {
  connectTimeout: REDIS_CONNECT_TIMEOUT_MS,
  maxRetriesPerRequest: 3,
  retryStrategy(times) {
    return Math.min(times * 200, 5000);
  },
});

/** Build a namespaced cache key from parts. */
export function cacheMakeKey(...parts: string[]): string {
  return parts.join(":");
}

/** Get a JSON-parsed value from Redis. */
export async function cacheGet<T>(key: string): Promise<T | null> {
  const raw = await redis.get(key);
  if (raw === null) return null;
  return JSON.parse(raw) as T;
}

/** Set a JSON-stringified value in Redis with TTL. */
export async function cacheSet(
  key: string,
  value: unknown,
  ttlSeconds: number,
): Promise<void> {
  await redis.set(key, JSON.stringify(value), "EX", ttlSeconds);
}

/** Delete a key from Redis. */
export async function cacheDelete(key: string): Promise<void> {
  await redis.del(key);
}

export async function closeRedis() {
  await redis.quit();
}
