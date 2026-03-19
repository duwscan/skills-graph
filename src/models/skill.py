"""Skill node model."""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, ClassVar
from uuid import uuid4

from neomodel import (  # type: ignore[import-untyped]
    ArrayProperty,
    DateTimeProperty,
    FloatProperty,
    IntegerProperty,
    RelationshipTo,
    StringProperty,
    StructuredNode,
)

from models.is_a_rel import IsARel
from models.related_to_rel import RelatedToRel
from models.requires_rel import RequiresRel

if TYPE_CHECKING:
    pass


def _normalize_list(values: list[str] | None) -> list[str]:
    """Normalize a list of strings.

    - Strips whitespace
    - Converts to lowercase
    - Removes empty strings
    - Removes duplicates while preserving order
    """
    if not values:
        return []
    seen: set[str] = set()
    result: list[str] = []
    for v in values:
        normalized = v.strip().lower()
        if normalized and normalized not in seen:
            seen.add(normalized)
            result.append(normalized)
    return result


class Skill(StructuredNode):
    """Skill node representing a learnable skill in the graph.

    Attributes:
        uid: Unique identifier (format: skill_[uuid])
        name: Human-readable skill name (unique, used as identifier)
        label: Display label for frontend (defaults to name if not provided)
        skill_type: Category (technical, soft, domain, tool)
        high_surface_forms: Primary surface forms/aliases
        low_surface_forms: Secondary surface forms/aliases
        abbreviations: Common abbreviations for the skill
        created_at: Creation timestamp
        updated_at: Last update timestamp
        version: Schema version for migrations
        confidence_score: Confidence in the skill definition (0.0-1.0)
    """

    SKILL_TYPE_CHOICES: ClassVar[set[str]] = {"technical", "soft", "domain", "tool"}

    uid = StringProperty(unique_index=True, required=True)
    name = StringProperty(unique_index=True, required=True)
    label = StringProperty(required=False)
    skill_type = StringProperty(required=True)
    high_surface_forms = ArrayProperty(StringProperty(), default=list)
    low_surface_forms = ArrayProperty(StringProperty(), default=list)
    abbreviations = ArrayProperty(StringProperty(), default=list)
    created_at = DateTimeProperty(default_now=True)
    updated_at = DateTimeProperty(default_now=True)
    version = IntegerProperty(default=1)
    confidence_score = FloatProperty(default=None)

    is_a = RelationshipTo("Skill", "IS_A", model=IsARel)
    requires = RelationshipTo("Skill", "REQUIRES", model=RequiresRel)
    related_to = RelationshipTo("Skill", "RELATED_TO", model=RelatedToRel)

    def pre_save(self) -> None:
        """Update timestamp and validate before saving."""
        self.updated_at = datetime.utcnow()  # type: ignore[assignment]
        self._validate_skill_type()
        self._validate_uid_format()
        self._normalize_surface_forms()

    def _normalize_surface_forms(self) -> None:
        self.high_surface_forms = _normalize_list(self.high_surface_forms)  # type: ignore[assignment]
        self.low_surface_forms = _normalize_list(self.low_surface_forms)  # type: ignore[assignment]
        self.abbreviations = _normalize_list(self.abbreviations)  # type: ignore[assignment]

    def _validate_skill_type(self) -> None:
        if self.skill_type not in self.SKILL_TYPE_CHOICES:
            valid = ", ".join(sorted(self.SKILL_TYPE_CHOICES))
            raise ValueError(f"skill_type must be one of: {valid}")

    def _validate_uid_format(self) -> None:
        if not self.uid.startswith("skill_"):
            raise ValueError("uid must start with 'skill_'")

    @classmethod
    def create_skill(
        cls,
        name: str,
        skill_type: str,
        label: str | None = None,
        high_surface_forms: list[str] | None = None,
        low_surface_forms: list[str] | None = None,
        abbreviations: list[str] | None = None,
        confidence_score: float | None = None,
    ) -> "Skill":
        """Create a new Skill with auto-generated UID."""
        uid = f"skill_{uuid4().hex[:24]}"
        normalized_name = name.strip()
        return cls(
            uid=uid,
            name=normalized_name,
            label=(label or normalized_name).strip(),
            skill_type=skill_type,
            high_surface_forms=_normalize_list(high_surface_forms),
            low_surface_forms=_normalize_list(low_surface_forms),
            abbreviations=_normalize_list(abbreviations),
            confidence_score=confidence_score,
        ).save()

    def increment_version(self) -> "Skill":
        """Increment version and save."""
        self.version += 1  # type: ignore[operator]
        return self.save()
