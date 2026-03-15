"""Smoke test for Neo4j connectivity and Skill model."""

from __future__ import annotations

from db import ensure_indexes, init_db, test_connection
from models import Skill


def main() -> None:
    print("=== Skills Graph Smoke Test ===\n")

    print("1. Testing Neo4j connection...")
    if test_connection():
        print("   ✓ Connection successful\n")
    else:
        print("   ✗ Connection failed - check your .env file\n")
        return

    print("2. Initializing neomodel...")
    init_db()
    print("   ✓ Neomodel initialized\n")

    print("3. Creating indexes...")
    ensure_indexes()
    print("   ✓ Indexes created\n")

    print("4. Testing Skill model operations...")
    try:
        python_skill = Skill.create_skill(
            name="Python",
            skill_type="technical",
            high_surface_forms=["Python programming", "Python language"],
            abbreviations=["py"],
            confidence_score=0.95,
        )
        print(f"   ✓ Created skill: {python_skill.name} (uid: {python_skill.uid})")

        programming_skill = Skill.create_skill(
            name="Programming",
            skill_type="technical",
            confidence_score=0.9,
        )
        print(
            f"   ✓ Created skill: {programming_skill.name} (uid: {programming_skill.uid})"
        )

        python_skill.is_a.connect(
            programming_skill,
            {"weight": 0.9, "level": 1, "confidence": 0.95},
        )
        print(f"   ✓ Created IS_A relationship: Python -> Programming")

        fetched = Skill.nodes.get(name="Python")
        print(f"   ✓ Fetched skill by name: {fetched.name}")

        print("\n=== All tests passed! ===")

    except Exception as e:
        print(f"   ✗ Error: {e}")


if __name__ == "__main__":
    main()
