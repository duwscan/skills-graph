import { Hono } from "hono";
import { cors } from "hono/cors";
import { config } from "./config/env";
import { healthRoutes } from "./routes/health";
import { requestId } from "./middleware/request-id";
import { errorHandler } from "./middleware/error-handler";
import { NotFoundError } from "./lib/errors";
import { closeDatabase } from "./db/client";
import { closeRedis } from "./db/redis";
import { GRACEFUL_SHUTDOWN_TIMEOUT_MS } from "./config/constants";

const app = new Hono();

// Middleware
app.use("*", cors());
app.use("*", requestId());

// Routes
app.route("/health", healthRoutes);
// Placeholder route groups (implemented in Phase 2+)
// app.route("/api/skills", skillRoutes);
// app.route("/api/edges", edgeRoutes);
// app.route("/api/extract", extractRoutes);
// app.route("/api/taxonomy", taxonomyRoutes);
// app.route("/api/review-queue", reviewRoutes);

// 404 catch-all
app.notFound((_c) => {
  throw new NotFoundError();
});

// Error handler
app.onError(errorHandler);

// Graceful shutdown
async function shutdown() {
  console.log("Shutting down gracefully...");
  const timeout = setTimeout(() => {
    console.error("Shutdown timed out, forcing exit.");
    process.exit(1);
  }, GRACEFUL_SHUTDOWN_TIMEOUT_MS);

  try {
    await Promise.all([closeDatabase(), closeRedis()]);
    clearTimeout(timeout);
    console.log("Shutdown complete.");
    process.exit(0);
  } catch (err) {
    console.error("Error during shutdown:", err);
    clearTimeout(timeout);
    process.exit(1);
  }
}

process.on("SIGTERM", shutdown);
process.on("SIGINT", shutdown);

console.log(
  `Skills Graph API started on port ${config.PORT} (${config.NODE_ENV})`,
);

export default {
  port: config.PORT,
  fetch: app.fetch,
};

export { app };
