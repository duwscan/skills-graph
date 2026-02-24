import { describe, it, expect } from "bun:test";
import { Hono } from "hono";
import { requestId } from "../../src/middleware/request-id";
import { errorHandler } from "../../src/middleware/error-handler";
import { NotFoundError } from "../../src/lib/errors";

interface HealthBody {
  status: string;
  version: string;
  db: string;
  redis: string;
  uptime_seconds: number;
}

interface ErrorBody {
  error: string;
  status: number;
  request_id?: string;
}

// Minimal test app that mocks DB/Redis for health check testing
function createHealthTestApp(opts: { dbOk?: boolean; redisOk?: boolean } = {}) {
  const { dbOk = true, redisOk = true } = opts;
  const app = new Hono();
  app.use("*", requestId());

  app.get("/health", async (c) => {
    const dbStatus = dbOk ? "connected" : "disconnected";
    const redisStatus = redisOk ? "connected" : "disconnected";
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
        uptime_seconds: 0,
      },
      statusCode,
    );
  });

  app.notFound(() => {
    throw new NotFoundError();
  });
  app.onError(errorHandler);

  return app;
}

describe("GET /health", () => {
  it("returns ok when DB and Redis are connected", async () => {
    const app = createHealthTestApp({ dbOk: true, redisOk: true });
    const res = await app.request("/health");
    expect(res.status).toBe(200);

    const body = (await res.json()) as HealthBody;
    expect(body.status).toBe("ok");
    expect(body.version).toBe("0.1.0");
    expect(body.db).toBe("connected");
    expect(body.redis).toBe("connected");
    expect(typeof body.uptime_seconds).toBe("number");
  });

  it("returns degraded when DB is down", async () => {
    const app = createHealthTestApp({ dbOk: false, redisOk: true });
    const res = await app.request("/health");
    expect(res.status).toBe(503);

    const body = (await res.json()) as HealthBody;
    expect(body.status).toBe("degraded");
    expect(body.db).toBe("disconnected");
    expect(body.redis).toBe("connected");
  });

  it("returns degraded when Redis is down", async () => {
    const app = createHealthTestApp({ dbOk: true, redisOk: false });
    const res = await app.request("/health");
    expect(res.status).toBe(503);

    const body = (await res.json()) as HealthBody;
    expect(body.status).toBe("degraded");
    expect(body.redis).toBe("disconnected");
  });

  it("returns degraded when both are down", async () => {
    const app = createHealthTestApp({ dbOk: false, redisOk: false });
    const res = await app.request("/health");
    expect(res.status).toBe(503);

    const body = (await res.json()) as HealthBody;
    expect(body.status).toBe("degraded");
    expect(body.db).toBe("disconnected");
    expect(body.redis).toBe("disconnected");
  });
});

describe("404 handling", () => {
  it("returns structured 404 for unknown routes", async () => {
    const app = createHealthTestApp();
    const res = await app.request("/api/nonexistent");
    expect(res.status).toBe(404);

    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Not Found");
    expect(body.status).toBe(404);
    expect(body.request_id).toBeDefined();
  });
});

describe("response headers", () => {
  it("includes x-request-id header", async () => {
    const app = createHealthTestApp();
    const res = await app.request("/health");
    const reqId = res.headers.get("x-request-id");
    expect(reqId).toBeDefined();
    expect(typeof reqId).toBe("string");
    expect(reqId!.length).toBeGreaterThan(0);
  });
});
