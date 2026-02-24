import { describe, it, expect } from "bun:test";
import { Hono } from "hono";
import { errorHandler } from "../../src/middleware/error-handler";
import { requestId } from "../../src/middleware/request-id";
import {
  AppError,
  NotFoundError,
  ConflictError,
  ValidationError,
  CycleDetectedError,
} from "../../src/lib/errors";

function createTestApp() {
  const app = new Hono();
  app.use("*", requestId());

  app.get("/not-found", () => {
    throw new NotFoundError();
  });
  app.get("/not-found-custom", () => {
    throw new NotFoundError("Skill not found");
  });
  app.get("/conflict", () => {
    throw new ConflictError("Slug already exists");
  });
  app.get("/validation", () => {
    throw new ValidationError("Invalid input");
  });
  app.get("/cycle", () => {
    throw new CycleDetectedError();
  });
  app.get("/app-error", () => {
    throw new AppError("Custom error", 418);
  });
  app.get("/generic", () => {
    throw new Error("Something went wrong");
  });

  app.onError(errorHandler);
  return app;
}

interface ErrorBody {
  error: string;
  status: number;
  request_id?: string;
  details?: unknown[];
}

describe("error handler", () => {
  const app = createTestApp();

  it("maps NotFoundError to 404", async () => {
    const res = await app.request("/not-found");
    expect(res.status).toBe(404);
    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Not Found");
    expect(body.status).toBe(404);
    expect(body.request_id).toBeDefined();
  });

  it("uses custom message for NotFoundError", async () => {
    const res = await app.request("/not-found-custom");
    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Skill not found");
  });

  it("maps ConflictError to 409", async () => {
    const res = await app.request("/conflict");
    expect(res.status).toBe(409);
    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Slug already exists");
  });

  it("maps ValidationError to 400", async () => {
    const res = await app.request("/validation");
    expect(res.status).toBe(400);
    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Invalid input");
  });

  it("maps CycleDetectedError to 422", async () => {
    const res = await app.request("/cycle");
    expect(res.status).toBe(422);
    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Cycle detected in graph");
  });

  it("maps custom AppError with custom status code", async () => {
    const res = await app.request("/app-error");
    expect(res.status).toBe(418);
    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Custom error");
  });

  it("maps generic Error to 500", async () => {
    const res = await app.request("/generic");
    expect(res.status).toBe(500);
    const body = (await res.json()) as ErrorBody;
    expect(body.error).toBe("Internal Server Error");
    expect(body.status).toBe(500);
  });

  it("always includes request_id in error responses", async () => {
    const res = await app.request("/generic");
    const body = (await res.json()) as ErrorBody;
    expect(body.request_id).toBeDefined();
    expect(typeof body.request_id).toBe("string");
  });
});
