import type { Context } from "hono";
import { createMiddleware } from "hono/factory";

export const requestId = () =>
  createMiddleware(async (c: Context, next) => {
    const id = crypto.randomUUID();
    c.set("requestId", id);
    c.header("x-request-id", id);
    await next();
  });
