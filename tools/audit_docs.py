#!/usr/bin/env python3
"""Validate the current-only documentation set and code-derived Codex facts."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CURRENT_DOCS = [
    ROOT / "README.md",
    ROOT / "AGENTS.md",
    ROOT / "SESSION.md",
    ROOT / "docs" / "README.md",
    ROOT / "docs" / "BUILDING_IN_SANDBOX.md",
    ROOT / "docs" / "KNOWN_ISSUES.md",
    ROOT / "docs" / "PROVENANCE.md",
    ROOT / "docs" / "THIRD_PARTY_NOTICES.md",
    ROOT / "Jujutsu Kaizen" / "jujutsumod-codebase-codex" / "00-MOC.md",
]
REMOVED_DOC_DIRS = [
    ROOT / "docs" / "research",
    ROOT / "docs" / "reviews",
    ROOT / "docs" / "session-handoffs",
    ROOT / "docs" / "superpowers",
    ROOT / "docs" / "specs",
    ROOT / "docs" / "vfx",
    ROOT / "docs" / "gui",
    ROOT / "docs" / "visual-targets",
]
FORBIDDEN_REFERENCES = [
    "docs/research/",
    "docs/reviews/",
    "docs/session-handoffs/",
    "docs/superpowers/",
    "docs/specs/",
    "docs/vfx/",
    "docs/gui/",
    "docs/visual-targets/",
    "feat/neon-gui-polish",
    ".worktrees/nobara-cinematic-slice",
    "e31a67e",
    "7f32273",
    "5073b24",
    "all 8 assertion tests",
    "8 assertion tests",
]
LINK_PATTERN = re.compile(r"(?<!!)\[[^\]]+\]\(([^)]+)\)")
VFX_ID_PATTERN = re.compile(r"public static final ResourceLocation [A-Z0-9_]+ = id\(")
TEST_TASK_PATTERN = re.compile(r"tasks\.register\('test[A-Za-z0-9]+'\s*,\s*JavaExec\)")


def markdown_files() -> list[Path]:
    return sorted(
        path
        for path in ROOT.rglob("*.md")
        if ".git" not in path.parts and "build" not in path.parts
    )


def code_metrics() -> dict[str, int]:
    mixins = json.loads((ROOT / "src/client/resources/jujutsumod.client.mixins.json").read_text(encoding="utf-8"))
    vfx_ids = (ROOT / "src/main/java/jujutsu/mod/vfx/NobaraVfxIds.java").read_text(encoding="utf-8")
    gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
    return {
        "main_java": len(list((ROOT / "src/main/java").rglob("*.java"))),
        "client_java": len(list((ROOT / "src/client/java").rglob("*.java"))),
        "test_java": len(list((ROOT / "src/test/java").rglob("*.java"))),
        "verification_programs": len(TEST_TASK_PATTERN.findall(gradle)),
        "client_mixins": len(mixins.get("client", [])),
        "nobara_vfx_ids": len(VFX_ID_PATTERN.findall(vfx_ids)),
    }


def validate_links(files: list[Path], errors: list[str]) -> None:
    for path in files:
        text = path.read_text(encoding="utf-8", errors="ignore")
        for raw_target in LINK_PATTERN.findall(text):
            target = raw_target.strip().split("#", 1)[0]
            if not target or target.startswith(("http://", "https://", "mailto:", "#")):
                continue
            resolved = (path.parent / target.replace("%20", " ")).resolve()
            if not resolved.exists():
                errors.append(f"broken relative link: {path.relative_to(ROOT)} -> {raw_target}")


def main() -> int:
    errors: list[str] = []
    files = markdown_files()

    for path in CURRENT_DOCS:
        if not path.exists():
            errors.append(f"missing current document: {path.relative_to(ROOT)}")

    for directory in REMOVED_DOC_DIRS:
        if directory.exists():
            errors.append(f"removed documentation directory exists: {directory.relative_to(ROOT)}")

    for path in files:
        text = path.read_text(encoding="utf-8", errors="ignore")
        for reference in FORBIDDEN_REFERENCES:
            if reference.lower() in text.lower():
                errors.append(f"removed/stale documentation reference {reference!r}: {path.relative_to(ROOT)}")
        if "Status: HISTORICAL REFERENCE" in text:
            errors.append(f"historical marker remains in current-only documentation: {path.relative_to(ROOT)}")

    validate_links(files, errors)
    metrics = code_metrics()
    moc = (ROOT / "Jujutsu Kaizen/jujutsumod-codebase-codex/00-MOC.md").read_text(encoding="utf-8", errors="ignore")
    metric_tokens = {
        "main_java": f"Main Java files | {metrics['main_java']}",
        "client_java": f"Client Java files | {metrics['client_java']}",
        "test_java": f"Test Java files | {metrics['test_java']}",
        "verification_programs": f"Verification programs | {metrics['verification_programs']}",
        "client_mixins": f"Client mixins | {metrics['client_mixins']}",
        "nobara_vfx_ids": f"Nobara VFX ids | {metrics['nobara_vfx_ids']}",
    }
    for key, token in metric_tokens.items():
        if token not in moc:
            errors.append(f"MOC metric is stale or missing for {key}: expected {token!r}")

    if errors:
        print("Documentation audit failed:")
        for error in errors:
            print(f"- {error}")
        return 1

    print(f"Documentation audit passed: {len(files)} current Markdown files, metrics={metrics}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
