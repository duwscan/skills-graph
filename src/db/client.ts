import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import { config } from "../config/env";
import { DB_POOL_SIZE, DB_CONNECT_TIMEOUT_MS } from "../config/constants";
import * as schema from "./schema";

const queryClient = postgres(config.DATABASE_URL, {
  max: DB_POOL_SIZE,
  connect_timeout: DB_CONNECT_TIMEOUT_MS / 1000,
});

export const db = drizzle(queryClient, { schema });

export async function closeDatabase() {
  await queryClient.end();
}
