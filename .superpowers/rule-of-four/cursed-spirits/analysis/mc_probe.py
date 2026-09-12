"""Fail-fast probe (Block 5 step 0): summon 3 LESSER cursed spirits, screenshot, report state.

Run from repo root: python analysis/mc_probe.py <tag>
"""
import json
import sys

sys.path.insert(0, ".superpowers/rule-of-four/cursed-spirits/analysis")
import mc_mcp as m  # noqa: E402

DIM = "minecraft:overworld"
TYPE = "jujutsumod:lesser_cursed_spirit"
tag = sys.argv[1] if len(sys.argv) > 1 else "probe1"

players = m.text_of(m.call("mod", "entity_query", {"dimension": DIM, "selector": "@a"}))
print("=== players ===")
print(players[:600])

m.call("mod", "command_execute", {"command": "time set noon"})
m.call("mod", "command_execute", {"command": "weather clear"})
m.call("mod", "command_execute", {"command": "kill @e[type=minecraft:slime,distance=..64]"})

# spawn a line of three in front of the player (player faces -Z at yaw -180)
import re
pos = re.search(r"x: ([\d.-]+)\s+y: ([\d.-]+)\s+z: ([\d.-]+)", players)
px, py, pz = float(pos.group(1)), float(pos.group(2)), float(pos.group(3))
spots = [(px - 1.5, py, pz - 5.0), (px, py, pz - 6.5), (px + 1.5, py, pz - 5.0)]
for sx, sy, sz in spots:
    res = m.call("mod", "entity_summon", {
        "dimension": DIM, "entity_type": TYPE,
        "position": {"x": round(sx, 2), "y": round(sy, 2), "z": round(sz, 2)},
    })
    print("summon ->", m.text_of(res)[:200])

report = m.text_of(m.call("mod", "entity_query", {"dimension": DIM, "selector": "@e", "limit": 40}))
print("=== @e (filtered) ===")
for block in report.split("\n- "):
    if "cursed" in block:
        print("- " + block.strip()[:400])
        print("---")

# freeze them so the capture is stable, then shoot
uuids = re.findall(r"uuid: ([0-9a-f-]{36})", report)
cursed_uuids = []
for i, uid in enumerate(uuids):
    nbt = m.text_of(m.call("mod", "entity_get_nbt", {"dimension": DIM, "uuid": uid}))
    if TYPE.split(":")[1] in nbt or "Variant" in nbt:
        cursed_uuids.append(uid)
        print(f"uuid {uid} nbt: {nbt[:300]}")
print("cursed count:", len(cursed_uuids), "of", len(uuids))

m.call("mod", "command_execute", {"command": f"tp @a {px} {py} {pz} 180 5"})

shot = f".superpowers/rule-of-four/cursed-spirits/analysis/{tag}.png"
result = m.call("upstream", "view_capture", {"downscale": 2})
saved = 0
for item in result.get("result", {}).get("content", []) or []:
    if item.get("type") == "image":
        import base64
        with open(shot, "wb") as fh:
            fh.write(base64.b64decode(item["data"]))
        saved += 1
print("capture saved:", saved, "->", shot)
print("capture text:", m.text_of(result)[:400])
