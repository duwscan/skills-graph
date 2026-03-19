"""Seed all root JSON datasets into Neo4j."""

from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Literal, TypedDict, cast

import neomodel

from core.db import ensure_indexes, init_db
from models import Skill

RelationshipType = Literal["IS_A", "REQUIRES", "RELATED_TO"]
VALID_RELATIONSHIP_TYPES: set[str] = {"IS_A", "REQUIRES", "RELATED_TO"}
RELATIONSHIP_QUERIES: dict[RelationshipType, str] = {
    "IS_A": (
        "MATCH (source:Skill {uid: $source_uid}) "
        "MATCH (target:Skill {uid: $target_uid}) "
        "MERGE (source)-[rel:IS_A]->(target) "
        "SET rel += $properties"
    ),
    "REQUIRES": (
        "MATCH (source:Skill {uid: $source_uid}) "
        "MATCH (target:Skill {uid: $target_uid}) "
        "MERGE (source)-[rel:REQUIRES]->(target) "
        "SET rel += $properties"
    ),
    "RELATED_TO": (
        "MATCH (source:Skill {uid: $source_uid}) "
        "MATCH (target:Skill {uid: $target_uid}) "
        "MERGE (source)-[rel:RELATED_TO]->(target) "
        "SET rel += $properties"
    ),
}


class SkillPayload(TypedDict):
    uid: str
    name: str
    label: str
    skill_type: str
    high_surface_forms: list[str]
    low_surface_forms: list[str]
    abbreviations: list[str]
    confidence_score: float | None


class RelationshipPayload(TypedDict):
    source_uid: str
    target_uid: str
    type: RelationshipType
    weight: float
    level: int
    confidence: float
    min_level: str
    is_mandatory: bool
    relation_type: str


class DatasetPayload(TypedDict):
    name: str
    description: str
    skills: list[SkillPayload]
    relationships: list[RelationshipPayload]


class RootPayload(TypedDict):
    industries: list[DatasetPayload]


@dataclass(frozen=True)
class SeedStats:
    files_loaded: int
    datasets_loaded: int
    skills_created: int
    skills_updated: int
    relationships_merged: int


def _merge_relationship(
    source_uid: str,
    target_uid: str,
    relationship_type: RelationshipType,
    properties: dict[str, Any],
) -> None:
    query = RELATIONSHIP_QUERIES.get(relationship_type)
    if query is None:
        raise ValueError(f"Unsupported relationship type: {relationship_type}")
    neomodel.db.cypher_query(
        query,
        {
            "source_uid": source_uid,
            "target_uid": target_uid,
            "properties": properties,
        },
    )


def _apply_skill_payload(existing: Skill, payload: SkillPayload) -> Skill:
    existing.label = payload.get("label", payload["name"])
    existing.skill_type = payload["skill_type"]
    existing.high_surface_forms = payload.get("high_surface_forms", [])
    existing.low_surface_forms = payload.get("low_surface_forms", [])
    existing.abbreviations = payload.get("abbreviations", [])
    existing.confidence_score = payload.get("confidence_score")
    return existing.save()


def _upsert_skill(payload: SkillPayload) -> tuple[Skill, bool]:
    by_uid = Skill.nodes.get_or_none(uid=payload["uid"])
    if by_uid is not None:
        return _apply_skill_payload(by_uid, payload), False

    by_name = Skill.nodes.get_or_none(name=payload["name"])
    if by_name is not None:
        return _apply_skill_payload(by_name, payload), False

    created = Skill(
        uid=payload["uid"],
        name=payload["name"],
        label=payload.get("label", payload["name"]),
        skill_type=payload["skill_type"],
        high_surface_forms=payload.get("high_surface_forms", []),
        low_surface_forms=payload.get("low_surface_forms", []),
        abbreviations=payload.get("abbreviations", []),
        confidence_score=payload.get("confidence_score"),
    ).save()
    return created, True


def _resolve_uid(
    original_uid: str,
    dataset_uid_map: dict[str, str],
    global_uid_map: dict[str, str],
) -> str | None:
    return dataset_uid_map.get(original_uid) or global_uid_map.get(original_uid)


def _validate_relationship_payload(
    relationship: RelationshipPayload, seed_file: Path, relationship_type: str
) -> None:
    required_fields = {
        "IS_A": ("weight", "level", "confidence"),
        "REQUIRES": ("weight", "min_level", "is_mandatory"),
        "RELATED_TO": ("weight", "relation_type"),
    }
    keys = required_fields.get(relationship_type)
    if keys is None:
        raise ValueError(f"Unsupported relationship type: {relationship_type}")
    missing = [key for key in keys if key not in relationship]
    if missing:
        raise ValueError(
            f"Relationship in {seed_file} is missing required fields for {relationship_type}: "
            f"{', '.join(missing)}"
        )


def _relationship_properties(
    relationship: RelationshipPayload, relationship_type: RelationshipType
) -> dict[str, Any]:
    if relationship_type == "IS_A":
        return {
            "weight": relationship["weight"],
            "level": relationship["level"],
            "confidence": relationship["confidence"],
        }
    if relationship_type == "REQUIRES":
        return {
            "weight": relationship["weight"],
            "min_level": relationship["min_level"],
            "is_mandatory": relationship["is_mandatory"],
        }
    if relationship_type == "RELATED_TO":
        return {
            "weight": relationship["weight"],
            "relation_type": relationship["relation_type"],
        }
    raise ValueError(f"Unsupported relationship type: {relationship_type}")


def _collect_seed_files(repo_root: Path, specified_files: list[str]) -> list[Path]:
    if specified_files:
        files = [Path(file_name).expanduser() for file_name in specified_files]
    else:
        seeds_dir = repo_root / "data" / "seeds"
        files = sorted(
            path
            for path in seeds_dir.glob("seed*.json")
            if path.name == "seed.json" or path.name.startswith("seed_")
        )

    resolved: list[Path] = []
    for path in files:
        candidate = path if path.is_absolute() else repo_root / path
        if not candidate.exists():
            raise FileNotFoundError(f"Seed file does not exist: {candidate}")
        resolved.append(candidate)
    return resolved


def seed_all(seed_files: list[Path]) -> SeedStats:
    init_db()
    ensure_indexes()

    skills_created = 0
    skills_updated = 0
    relationships_merged = 0
    datasets_loaded = 0
    global_uid_map: dict[str, str] = {}

    for seed_file in seed_files:
        with seed_file.open("r", encoding="utf-8") as stream:
            payload: RootPayload = json.load(stream)

        for dataset in payload.get("industries", []):
            datasets_loaded += 1
            uid_map: dict[str, str] = {}

            for skill_payload in dataset.get("skills", []):
                skill, was_created = _upsert_skill(skill_payload)
                uid_map[skill_payload["uid"]] = skill.uid
                global_uid_map[skill_payload["uid"]] = skill.uid
                if was_created:
                    skills_created += 1
                else:
                    skills_updated += 1

            for relationship in dataset.get("relationships", []):
                source_uid = _resolve_uid(
                    relationship["source_uid"],
                    uid_map,
                    global_uid_map,
                )
                target_uid = _resolve_uid(
                    relationship["target_uid"],
                    uid_map,
                    global_uid_map,
                )
                if source_uid is None or target_uid is None:
                    missing_uid = (
                        relationship["source_uid"]
                        if source_uid is None
                        else relationship["target_uid"]
                    )
                    raise ValueError(
                        f"Relationship references unknown skill uid '{missing_uid}' in {seed_file}"
                    )

                relationship_type = relationship["type"]
                if relationship_type not in VALID_RELATIONSHIP_TYPES:
                    raise ValueError(f"Unsupported relationship type: {relationship_type}")
                _validate_relationship_payload(relationship, seed_file, relationship_type)
                typed_relationship = cast(RelationshipType, relationship_type)
                props = _relationship_properties(relationship, typed_relationship)

                _merge_relationship(
                    source_uid=source_uid,
                    target_uid=target_uid,
                    relationship_type=typed_relationship,
                    properties=props,
                )
                relationships_merged += 1

    return SeedStats(
        files_loaded=len(seed_files),
        datasets_loaded=datasets_loaded,
        skills_created=skills_created,
        skills_updated=skills_updated,
        relationships_merged=relationships_merged,
    )


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Seed all skills and relationships from root seed JSON files."
    )
    parser.add_argument(
        "--file",
        action="append",
        default=[],
        help="Seed file path to include (can be passed multiple times). Defaults to data/seeds/seed*.json.",
    )
    return parser.parse_args()


def main() -> None:
    args = _parse_args()
    repo_root = Path.cwd()
    seed_files = _collect_seed_files(repo_root=repo_root, specified_files=args.file)
    stats = seed_all(seed_files)
    print(
        "Seed complete: "
        f"files={stats.files_loaded}, datasets={stats.datasets_loaded}, "
        f"skills_created={stats.skills_created}, skills_updated={stats.skills_updated}, "
        f"relationships_merged={stats.relationships_merged}"
    )


if __name__ == "__main__":
    main()
