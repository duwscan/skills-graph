"""Centralized AI provider configuration.

Single source of truth for all AI-related settings.

Usage::

    from core.ai import AI_CONFIG

    AI_CONFIG["provider"]                       # "ollama"
    AI_CONFIG["model_name"]                     # "llama3.1"
    AI_CONFIG["openai"]["api_key"]              # ""
    AI_CONFIG["ollama"]["base_url"]             # "http://localhost:11434"
    AI_CONFIG["embedding"]["model"]             # "all-MiniLM-L6-v2"
    AI_CONFIG["embedding"]["semantic_threshold"] # 0.9
    AI_CONFIG["langsmith"]["enabled"]           # False
    AI_CONFIG["langsmith"]["project"]           # "skills-graph"
"""

from __future__ import annotations

import os

from dotenv import load_dotenv

load_dotenv()

_provider = os.environ.get("LLM_PROVIDER", "ollama")
_default_model = "gpt-4o-mini" if _provider == "openai" else "llama3.1"

_langsmith_api_key = os.environ.get("LANGSMITH_API_KEY", "")
_langsmith_enabled = _langsmith_api_key != "" and os.environ.get(
    "LANGSMITH_TRACING", "false"
).lower() in ("true", "1", "yes")

# Set the env vars that LangChain reads automatically
if _langsmith_enabled:
    os.environ["LANGCHAIN_TRACING_V2"] = "true"
    os.environ["LANGCHAIN_API_KEY"] = _langsmith_api_key
    if os.environ.get("LANGSMITH_ENDPOINT"):
        os.environ["LANGCHAIN_ENDPOINT"] = os.environ["LANGSMITH_ENDPOINT"]
    if os.environ.get("LANGSMITH_PROJECT"):
        os.environ["LANGCHAIN_PROJECT"] = os.environ["LANGSMITH_PROJECT"]

AI_CONFIG: dict = {
    "provider": _provider,
    "model_name": os.environ.get("LLM_MODEL_NAME") or _default_model,
    "temperature": float(os.environ.get("LLM_TEMPERATURE", "0.0")),
    "openai": {
        "api_key": os.environ.get("OPENAI_API_KEY", ""),
        "base_url": os.environ.get("OPENAI_BASE_URL", ""),
    },
    "ollama": {
        "base_url": os.environ.get("OLLAMA_BASE_URL", "http://localhost:11434"),
    },
    "embedding": {
        "model": os.environ.get("EMBEDDING_MODEL", "all-MiniLM-L6-v2"),
        "semantic_threshold": float(os.environ.get("SEMANTIC_THRESHOLD", "0.9")),
    },
    "langsmith": {
        "enabled": _langsmith_enabled,
        "api_key": _langsmith_api_key,
        "endpoint": os.environ.get("LANGSMITH_ENDPOINT", "https://api.smith.langchain.com"),
        "project": os.environ.get("LANGSMITH_PROJECT", "skills-graph"),
    },
}
