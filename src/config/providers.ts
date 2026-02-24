import { createProviderRegistry } from "ai";
import { createAnthropic } from "@ai-sdk/anthropic";
import { createOpenAI } from "@ai-sdk/openai";
import { config } from "./env";
import { EMBEDDING_MODEL } from "./constants";

const heliconeEnabled = !!config.HELICONE_API_KEY;

const anthropic = createAnthropic(
  heliconeEnabled
    ? {
        baseURL: "https://anthropic.helicone.ai/v1",
        headers: { "Helicone-Auth": `Bearer ${config.HELICONE_API_KEY}` },
      }
    : {},
);

const openai = createOpenAI(
  heliconeEnabled
    ? {
        baseURL: "https://oai.helicone.ai/v1",
        headers: { "Helicone-Auth": `Bearer ${config.HELICONE_API_KEY}` },
      }
    : {},
);

export const registry = createProviderRegistry({ anthropic, openai });

export function getExtractionModel(
  tier: "fast" | "standard" | "complex" = "standard",
) {
  switch (tier) {
    case "fast":
      return anthropic("claude-haiku-4-5-20251001");
    case "standard":
      return anthropic("claude-haiku-4-5-20251001");
    case "complex":
      return anthropic("claude-sonnet-4-5-20250929");
  }
}

export function getEmbeddingModel() {
  return openai.textEmbeddingModel(EMBEDDING_MODEL);
}

export function getClassificationModel() {
  return anthropic("claude-sonnet-4-5-20250929");
}
