import { z } from "zod";

const envSchema = z.object({
  DATABASE_URL: z.string().startsWith("postgresql://"),
  REDIS_URL: z.string().startsWith("redis://"),
  ANTHROPIC_API_KEY: z.string().min(1),
  OPENAI_API_KEY: z.string().min(1),
  TYPESENSE_URL: z.string().default("http://localhost:8108"),
  TYPESENSE_API_KEY: z.string().min(1),
  HELICONE_API_KEY: z.string().optional(),
  PORT: z.coerce.number().default(3000),
  NODE_ENV: z
    .enum(["development", "production", "test"])
    .default("development"),
});

const parsed = envSchema.safeParse(process.env);

if (!parsed.success) {
  console.error("Invalid environment configuration:");
  console.error(JSON.stringify(parsed.error.issues, null, 2));
  process.exit(1);
}

export const config = parsed.data;
