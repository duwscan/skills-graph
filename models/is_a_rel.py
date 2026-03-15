"""IS_A relationship model for Skill hierarchy."""

from __future__ import annotations

from neomodel import FloatProperty, IntegerProperty, StructuredRel  # type: ignore[import-untyped]


class IsARel(StructuredRel):
    """IS_A relationship between Skills (hierarchy).

    Attributes:
        weight: Relationship strength (0.0-1.0)
        level: Hierarchy level (1 or 2)
        confidence: Confidence score (0.0-1.0)
    """

    weight = FloatProperty(required=True)
    level = IntegerProperty(required=True)
    confidence = FloatProperty(required=True)
