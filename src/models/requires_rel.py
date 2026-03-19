"""REQUIRES relationship model for Skill dependencies."""

from __future__ import annotations

from neomodel import BooleanProperty, FloatProperty, StringProperty, StructuredRel  # type: ignore[import-untyped]


class RequiresRel(StructuredRel):
    """REQUIRES relationship between Skills (dependencies).

    Weight ranges:
        0.8-1.0: mandatory
        0.5-0.7: recommended

    Attributes:
        weight: Relationship strength (0.0-1.0)
        min_level: Minimum proficiency level required
        is_mandatory: Whether this dependency is mandatory
    """

    weight = FloatProperty(required=True)
    min_level = StringProperty(
        required=True,
        choices={
            "beginner": "beginner",
            "intermediate": "intermediate",
            "advanced": "advanced",
        },
    )
    is_mandatory = BooleanProperty(required=True)
