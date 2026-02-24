import { Hono } from "hono";
import { db } from "../db/client";
import { redis } from "../db/redis";
import { sql } from "drizzle-orm";

export const healthRoutes = new Hono();

const startedAt = Date.now();

healthRoutes.get("/", async (c) => {
  let dbStatus = "connected";
  let redisStatus = "connected";

  try {
    await db.execute(sql`SELECT 1`);
  } catch {
    dbStatus = "disconnected";
  }

  try {
    await redis.ping();
  } catch {
    redisStatus = "disconnected";
  }

  const status =
    dbStatus === "connected" && redisStatus === "connected"
      ? "ok"
      : "degraded";

  const statusCode = status === "ok" ? 200 : 503;

  return c.json(
    {
      status,
      version: "0.1.0",
      db: dbStatus,
      redis: redisStatus,
      uptime_seconds: Math.floor((Date.now() - startedAt) / 1000),
    },
    statusCode,
  );
});
