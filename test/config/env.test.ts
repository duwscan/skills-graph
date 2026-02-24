import { describe, it, expect } from "bun:test";
import { z } from "zod";

// Re-create the schema inline so we can test validation
// without triggering the fail-fast process.exit in env.ts
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

const VALID_ENV = {
  DATABASE_URL: "postgresql://skills:skills_dev@localhost:5432/skills_graph",
  REDIS_URL: "redis://localhost:6379",
  ANTHROPIC_API_KEY: "sk-ant-test",
  OPENAI_API_KEY: "sk-test",
  TYPESENSE_API_KEY: "test_key",
};

describe("env config validation", () => {
  it("accepts valid environment variables", () => {
    const result = envSchema.safeParse(VALID_ENV);
    expect(result.success).toBe(true);
  });

  it("rejects missing DATABASE_URL", () => {
    const { DATABASE_URL, ...rest } = VALID_ENV;
    const result = envSchema.safeParse(rest);
    expect(result.success).toBe(false);
  });

  it("rejects invalid DATABASE_URL prefix", () => {
    const result = envSchema.safeParse({
      ...VALID_ENV,
      DATABASE_URL: "mysql://localhost",
    });
    expect(result.success).toBe(false);
  });

  it("rejects missing ANTHROPIC_API_KEY", () => {
    const { ANTHROPIC_API_KEY, ...rest } = VALID_ENV;
    const result = envSchema.safeParse(rest);
    expect(result.success).toBe(false);
  });

  it("rejects empty OPENAI_API_KEY", () => {
    const result = envSchema.safeParse({ ...VALID_ENV, OPENAI_API_KEY: "" });
    expect(result.success).toBe(false);
  });

  it("rejects missing TYPESENSE_API_KEY", () => {
    const { TYPESENSE_API_KEY, ...rest } = VALID_ENV;
    const result = envSchema.safeParse(rest);
    expect(result.success).toBe(false);
  });

  it("defaults PORT to 3000", () => {
    const result = envSchema.safeParse(VALID_ENV);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.PORT).toBe(3000);
    }
  });

  it("coerces PORT string to number", () => {
    const result = envSchema.safeParse({ ...VALID_ENV, PORT: "8080" });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.PORT).toBe(8080);
    }
  });

  it("defaults NODE_ENV to development", () => {
    const result = envSchema.safeParse(VALID_ENV);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.NODE_ENV).toBe("development");
    }
  });

  it("rejects invalid NODE_ENV", () => {
    const result = envSchema.safeParse({ ...VALID_ENV, NODE_ENV: "staging" });
    expect(result.success).toBe(false);
  });

  it("defaults TYPESENSE_URL when not set", () => {
    const result = envSchema.safeParse(VALID_ENV);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.TYPESENSE_URL).toBe("http://localhost:8108");
    }
  });

  it("allows missing HELICONE_API_KEY (optional)", () => {
    const result = envSchema.safeParse(VALID_ENV);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.HELICONE_API_KEY).toBeUndefined();
    }
  });
});
