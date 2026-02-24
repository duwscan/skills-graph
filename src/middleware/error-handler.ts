import type { Context } from "hono";
import { ZodError } from "zod";
import { AppError } from "../lib/errors";

export function errorHandler(err: Error, c: Context) {
  const requestId = c.get("requestId") as string | undefined;

  if (err instanceof AppError) {
    return c.json(
      { error: err.message, status: err.statusCode, request_id: requestId },
      err.statusCode as 400,
    );
  }

  if (err instanceof ZodError) {
    return c.json(
      {
        error: "Validation Error",
        status: 400,
        request_id: requestId,
        details: err.issues,
      },
      400,
    );
  }

  console.error(`[${requestId}] Unhandled error:`, err);

  return c.json(
    { error: "Internal Server Error", status: 500, request_id: requestId },
    500,
  );
}
