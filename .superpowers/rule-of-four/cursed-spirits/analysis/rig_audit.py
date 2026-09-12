"""Pre-port rig audit: model part names vs animation clip bone names, per frozen variant.

Read-only analysis over the CFR decompiled Sons of Sins sources.
Usage: python rig_audit.py
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "decompiled" / "net" / "mcreator" / "sonsofsins" / "client"

# frozen roster: variant -> (model class file stem, animation class file stem)
ROSTER = {
    "PROWLER": ("model/Modelprowler", "animation/prowlerAnimation"),
    "FLOATING_CURSE": ("model/Modelcurse_ghost", "animation/ghost/curse_ghostAnimation"),
    "GULBER": ("model/Modelgulber", "animation/gulberAnimation"),
    "KELVIN": ("model/Modelkelvin", "animation/kelvinAnimation"),
    "BUTCHER": ("model/Modelbutcher", "animation/butcherAnimation"),
    "GUZZLER": ("model/Modelguzzler", "animation/guzzlerAnimation"),
    "BLUD": ("model/Modelblud", "animation/bludAnimation"),
    "WALKING_BED": ("model/Modelwalking_bed", "animation/walking_bedAnimation"),
    "WISTIVER": ("model/Modelwistiver", "animation/wistiverAnimation"),
}

part_re = re.compile(r'addOrReplaceChild\(\s*"([^"]+)"')
child_re = re.compile(r'getChild\(\s*"([^"]+)"')
clip_re = re.compile(r'public static final AnimationDefinition (\w+)\s*=\s*AnimationDefinition\.Builder\.withLength\((?:\s*\(float\))?\s*([\d.]+)f\)(\.looping\(\))?')
bone_re = re.compile(r'addAnimation\(\s*"([^"]+)"')
chan_re = re.compile(r'AnimationChannel\(AnimationChannel\.Targets\.(\w+)')

def load(p: Path) -> str:
    return p.read_text(encoding="utf-8", errors="replace")

for variant, (model_rel, anim_rel) in ROSTER.items():
    mp = ROOT / f"{model_rel}.java"
    ap = ROOT / f"{anim_rel}.java"
    print(f"=== {variant} ===")
    if not mp.exists():
        print(f"  MODEL MISSING: {mp}")
        continue
    msrc = load(mp)
    parts = set(part_re.findall(msrc)) | set(child_re.findall(msrc))
    print(f"  model parts ({len(parts)}): {sorted(parts)}")
    if not ap.exists():
        print(f"  ANIMATION MISSING: {ap}")
        continue
    asrc = load(ap)
    clips = {}
    # split per constant to attribute bones correctly
    matches = list(clip_re.finditer(asrc))
    for i, m in enumerate(matches):
        start = m.end()
        end = matches[i + 1].start() if i + 1 < len(matches) else len(asrc)
        body = asrc[start:end]
        bones = sorted(set(bone_re.findall(body)))
        channels = sorted(set(chan_re.findall(body)))
        clips[m.group(1)] = {"len": m.group(2), "loop": bool(m.group(3)), "bones": bones, "targets": channels}
    for name, info in clips.items():
        missing = [b for b in info["bones"] if b not in parts]
        flag = "  <-- MISSING BONES: " + ", ".join(missing) if missing else ""
        print(f"  clip {name}: len={info['len']}s loop={info['loop']} bones={info['bones']} targets={info['targets']}{flag}")
    # coverage: model parts never animated
    animated = set()
    for info in clips.values():
        animated |= set(info["bones"])
    idle = sorted(animated - parts)
    print(f"  clip-bones not in model (all clips): {idle if idle else 'none'}")
