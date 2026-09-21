"""Generate the first data-driven cursed-object look set for issue #110.

The runtime deliberately keeps object type data in Java while this script owns the
repetitive geometry/texture files.  Re-running it is deterministic and overwrites
only the generated cursed-object/talisman assets.
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "jujutsumod"
GEO_DIR = ASSETS / "geckolib" / "models"
ITEM_DIR = ASSETS / "items"
MODEL_DIR = ASSETS / "models" / "item"
TEXTURE_DIR = ASSETS / "textures" / "item"
ANIMATION_DIR = ASSETS / "geckolib" / "animations"

# Each entry has a deliberately different silhouette family.  Coordinates are
# Minecraft model units and UVs are kept inside the 64x64 atlas.
OBJECTS = {
    "sukuna_finger": {
        "family": "elongated",
        "base": (31, 9, 13),
        "accent": (171, 30, 42),
        "cubes": [
            ((-2, 0, -2), (4, 8, 4), (0, 0)),
            ((-2, 7, -2), (4, 8, 4), (16, 0)),
            ((-2, 14, -2), (4, 7, 4), (32, 0)),
            ((-1, 20, -1), (2, 4, 2), (48, 0)),
        ],
    },
    "cursed_nail": {
        "family": "elongated",
        "base": (35, 38, 42),
        "accent": (211, 72, 92),
        "cubes": [
            ((-1, -8, -1), (2, 18, 2), (0, 0)),
            ((-2, 8, -2), (4, 3, 4), (12, 0)),
            ((-3, 11, -3), (6, 2, 6), (24, 0)),
        ],
    },
    "cursed_doll": {
        "family": "humanoid",
        "base": (46, 26, 18),
        "accent": (194, 53, 76),
        "cubes": [
            ((-4, 0, -2), (8, 9, 4), (0, 0)),
            ((-3, 9, -3), (6, 6, 6), (20, 0)),
            ((-7, 1, -1), (3, 7, 3), (36, 0)),
            ((4, 1, -1), (3, 7, 3), (44, 0)),
            ((-3, -6, -1), (3, 6, 3), (52, 0)),
            ((1, -6, -1), (3, 6, 3), (56, 0)),
        ],
    },
    "cursed_eye": {
        "family": "orbital",
        "base": (23, 36, 39),
        "accent": (210, 32, 64),
        "cubes": [
            ((-5, -3, -3), (10, 6, 6), (0, 0)),
            ((-3, -5, -2), (6, 10, 4), (24, 0)),
            ((-2, -2, -5), (4, 4, 10), (40, 0)),
            ((-1, -1, -6), (2, 2, 2), (56, 0)),
        ],
    },
    "cursed_coin": {
        "family": "planar",
        "base": (48, 32, 12),
        "accent": (222, 131, 31),
        "cubes": [
            ((-6, -1, -1), (12, 2, 2), (0, 0)),
            ((-5, -2, -2), (10, 1, 4), (28, 0)),
            ((-5, 1, -2), (10, 1, 4), (44, 0)),
        ],
    },
    "cursed_idol": {
        "family": "totem",
        "base": (41, 27, 17),
        "accent": (229, 92, 35),
        "cubes": [
            ((-5, 0, -5), (10, 3, 10), (0, 0)),
            ((-4, 3, -4), (8, 4, 8), (20, 0)),
            ((-3, 7, -3), (6, 5, 6), (36, 0)),
            ((-2, 12, -2), (4, 6, 4), (52, 0)),
        ],
    },
    "cursed_mask": {
        "family": "planar",
        "base": (31, 34, 40),
        "accent": (164, 59, 195),
        "cubes": [
            ((-6, 0, -1), (12, 2, 2), (0, 0)),
            ((-5, 2, -2), (10, 4, 2), (28, 0)),
            ((-4, 6, -2), (8, 5, 2), (44, 0)),
            ((-2, 11, -2), (4, 2, 2), (56, 0)),
        ],
    },
    "cursed_chain": {
        "family": "elongated",
        "base": (40, 42, 46),
        "accent": (70, 165, 201),
        "cubes": [
            ((-2, 0, -1), (4, 3, 2), (0, 0)),
            ((-1, 3, -2), (2, 4, 4), (12, 0)),
            ((-2, 7, -1), (4, 3, 2), (24, 0)),
            ((-1, 10, -2), (2, 4, 4), (36, 0)),
            ((-2, 14, -1), (4, 3, 2), (48, 0)),
        ],
    },
}

TALISMANS = {
    "sealing_talisman": ((183, 43, 49), (241, 214, 120)),
    "inscribed_talisman": ((57, 104, 185), (165, 213, 241)),
    "prismatic_talisman": ((117, 49, 177), (242, 130, 221)),
}


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def geo_for(type_id: str, spec: dict) -> dict:
    cubes = []
    for origin, size, uv in spec["cubes"]:
        cubes.append({"origin": list(origin), "size": list(size), "uv": list(uv)})
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.jujutsumod.cursed_object_{type_id}",
                "texture_width": 64,
                "texture_height": 64,
                "visible_bounds_width": 3,
                "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 0.75, 0],
            },
            "bones": [{
                "name": "root",
                "pivot": [0, 0, 0],
                "cubes": cubes,
            }],
        }],
    }


def object_texture(spec: dict, variant: int) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (*spec["base"], 255))
    draw = ImageDraw.Draw(image)
    accent = spec["accent"]
    # Variant one shifts the rune rhythm and darkens the base, making the two
    # looks distinct without depending on external art software.
    if variant:
        dark = tuple(max(0, channel - 9) for channel in spec["base"])
        image.paste((*dark, 255), (0, 0, 64, 64))
    step = 8 if variant == 0 else 11
    for offset in range(-64, 64, step):
        draw.line((offset, 0, offset + 64, 64), fill=(*accent, 255), width=2)
    for x in range(4 + variant, 64, 16):
        for y in range(4, 64, 16):
            draw.rectangle((x, y, x + 3, y + 3), fill=(*accent, 255))
    draw.rectangle((1, 1, 62, 62), outline=(*accent, 255), width=2)
    return image


def talisman_texture(base: tuple[int, int, int], accent: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", (64, 64), (*base, 255))
    draw = ImageDraw.Draw(image)
    draw.rectangle((6, 4, 57, 59), outline=(*accent, 255), width=3)
    for x in range(12, 56, 11):
        draw.line((x, 9, x - 7, 54), fill=(*accent, 255), width=2)
    draw.polygon([(31, 11), (39, 31), (31, 51), (23, 31)], outline=(*accent, 255), fill=None)
    return image


def main() -> None:
    for directory in (GEO_DIR, ITEM_DIR, MODEL_DIR, TEXTURE_DIR, ANIMATION_DIR):
        directory.mkdir(parents=True, exist_ok=True)

    for type_id, spec in OBJECTS.items():
        write_json(GEO_DIR / f"cursed_object_{type_id}.geo.json", geo_for(type_id, spec))
        for variant in (0, 1):
            object_texture(spec, variant).save(
                TEXTURE_DIR / f"cursed_object_{type_id}_{variant}.png", format="PNG")

    # 1.21.8's native item-definition format delegates the actual 3D render to
    # GeckoLib while the display model keeps vanilla inventory transforms.
    write_json(ITEM_DIR / "cursed_object.json", {
        "model": {
            "type": "minecraft:special",
            "base": "jujutsumod:item/cursed_object",
            "model": {"type": "geckolib:geckolib"},
        }
    })
    write_json(MODEL_DIR / "cursed_object.json", {
        "parent": "builtin/entity",
        "display": {
            "gui": {"rotation": [18, -28, 0], "scale": [0.72, 0.72, 0.72]},
            "ground": {"translation": [0, 2, 0], "scale": [0.48, 0.48, 0.48]},
            "firstperson_righthand": {"rotation": [0, 155, 12], "translation": [1.2, 2.2, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [0, -155, -12], "translation": [-1.2, 2.2, 0], "scale": [0.6, 0.6, 0.6]},
        },
    })

    # A no-op idle clip keeps GeckoLib's animation resource contract valid even
    # though the first object release intentionally has no moving controllers.
    write_json(ANIMATION_DIR / "cursed_object.animation.json", {
        "format_version": "1.8.0",
        "animations": {
            "animation.cursed_object.idle": {
                "loop": True,
                "animation_length": 1.0,
                "bones": {},
            }
        },
    })

    for item_id, (base, accent) in TALISMANS.items():
        write_json(ITEM_DIR / f"{item_id}.json", {
            "model": {
                "type": "minecraft:model",
                "model": f"jujutsumod:item/{item_id}",
            }
        })
        write_json(MODEL_DIR / f"{item_id}.json", {
            "parent": "minecraft:item/generated",
            "textures": {"layer0": f"jujutsumod:item/{item_id}"},
        })
        talisman_texture(base, accent).save(TEXTURE_DIR / f"{item_id}.png", format="PNG")

    print(f"generated {len(OBJECTS)} cursed-object models, {len(OBJECTS) * 2} looks, and {len(TALISMANS)} talisman assets")


if __name__ == "__main__":
    main()
