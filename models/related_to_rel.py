"""RELATED_TO relationship model for Skill similarity."""

from __future__ import annotations

from neomodel import FloatProperty, StringProperty, StructuredRel  # type: ignore[import-untyped]


class RelatedToRel(StructuredRel):
    """RELATED_TO relationship between Skills (similarity/complementarity).

    Attributes:
        weight: Relevance score (0.0-1.0)
        relation_type: Type of relation (similar or complementary)
    """

    weight = FloatProperty(required=True)
    relation_type = StringProperty(
        required=True,
        choices={"similar": "similar", "complementary": "complementary"},
    )
