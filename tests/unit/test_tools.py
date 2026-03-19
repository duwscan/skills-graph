"""Unit tests for agents/tools.py — Lucene escaping and helpers."""

from __future__ import annotations

from agents.skill_detection.tools import _build_composite_text, _escape_lucene


def test_escape_lucene_special_chars() -> None:
    assert _escape_lucene("C++") == r"C\+\+"
    assert _escape_lucene("C#") == "C#"  # '#' is not a Lucene special char
    assert _escape_lucene("Node.js") == r"Node\.js"
    assert _escape_lucene("AT&T") == r"AT\&T"


def test_escape_lucene_plain_text() -> None:
    assert _escape_lucene("Python") == "Python"
    assert _escape_lucene("Java") == "Java"
    assert _escape_lucene("machine learning") == "machine learning"


def test_escape_lucene_empty() -> None:
    assert _escape_lucene("") == ""


def test_build_composite_text_full() -> None:
    skill = {
        "name": "Python",
        "high_surface_forms": ["Python programming", "Python language"],
        "low_surface_forms": ["py scripting"],
        "abbreviations": ["py"],
    }
    text = _build_composite_text(skill)
    assert "Python" in text
    assert "Python programming" in text
    assert "py" in text
    assert "py scripting" in text


def test_build_composite_text_minimal() -> None:
    skill = {
        "name": "Java",
        "high_surface_forms": [],
        "low_surface_forms": [],
        "abbreviations": [],
    }
    text = _build_composite_text(skill)
    assert text == "Java"


def test_build_composite_text_none_fields() -> None:
    skill = {
        "name": "React",
        "high_surface_forms": None,
        "low_surface_forms": None,
        "abbreviations": None,
    }
    text = _build_composite_text(skill)
    assert text == "React"
