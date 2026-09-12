"""Throwaway: wrap >700-char lines in implementation-plan.md so agent readers (768-char truncation) see full contracts.

Splits long lines at sentence/clause boundaries; continuation lines get a bullet indent
matching the original construct. Idempotent-ish: run once; verify no line > 700.
"""
import re
from pathlib import Path

PLAN = Path(__file__).resolve().parents[1] / "implementation-plan.md"
MAX = 620  # target width for produced lines

text = PLAN.read_text(encoding="utf-8")
lines = text.splitlines()
out, changed = [], []


def split_line(line: str) -> list[str]:
    stripped = line.lstrip()
    indent = line[: len(line) - len(stripped)]
    bullet = bool(re.match(r"-\s|-\s\[|\d+\.\s", stripped))
    cont_indent = indent + ("    - " if stripped.startswith("- ") else "    ")

    # split points: after "; " and after ". " (sentence end), keep delimiters
    parts = re.split(r"(?<=;)\s+|(?<=\.)\s+(?=[A-Z(`])", line)
    chunks, cur = [], ""
    for p in parts:
        cand = (cur + " " + p).strip() if cur else p
        if len(cand) > MAX and cur:
            chunks.append(cur)
            cur = cont_indent + p
        else:
            cur = cand
    if cur:
        chunks.append(cur)
    return chunks


for i, line in enumerate(lines, 1):
    if len(line) > 700:
        chunks = split_line(line)
        out.extend(chunks)
        changed.append((i, len(line), [len(c) for c in chunks]))
    else:
        out.append(line)

PLAN.write_text("\n".join(out) + "\n", encoding="utf-8")

print("wrapped lines:")
for n, before, after in changed:
    print(f"  {n}: {before} -> {after}")
new_lines = PLAN.read_text(encoding="utf-8").splitlines()
over = [(i + 1, len(l)) for i, l in enumerate(new_lines) if len(l) > 700]
print("lines still > 700:", over)
print("total lines:", len(new_lines))
