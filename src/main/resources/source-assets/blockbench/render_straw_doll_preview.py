from __future__ import annotations

import json
import math
from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[5]
GEOMETRY = ROOT / "src/main/resources/assets/jujutsumod/geo/straw_doll.geo.json"
TEXTURE = ROOT / "src/main/resources/assets/jujutsumod/textures/item/straw_doll.png"
OUTPUT = ROOT / "build/asset-previews"


def rotate(point: tuple[float, float, float], pivot: list[float], degrees: list[float]) -> tuple[float, float, float]:
    x, y, z = (point[index] - pivot[index] for index in range(3))
    rx, ry, rz = (math.radians(value) for value in degrees)
    y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
    x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
    x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
    return x + pivot[0], y + pivot[1], z + pivot[2]


def texture_color(texture: Image.Image, uv: list[float]) -> tuple[int, int, int]:
    left = max(0, min(texture.width - 1, int(uv[0])))
    top = max(0, min(texture.height - 1, int(uv[1])))
    right = min(texture.width, left + 4)
    bottom = min(texture.height, top + 4)
    pixels = list(texture.crop((left, top, right, bottom)).convert("RGB").get_flattened_data())
    return tuple(round(sum(pixel[channel] for pixel in pixels) / len(pixels)) for channel in range(3))


def cube_faces(cube: dict, bone: dict, texture: Image.Image) -> list[tuple[list[tuple[float, float, float]], tuple[int, int, int]]]:
    ox, oy, oz = cube["origin"]
    sx, sy, sz = cube["size"]
    corners = [
        (ox + dx * sx, oy + dy * sy, oz + dz * sz)
        for dx, dy, dz in ((0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0), (0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1))
    ]
    if "rotation" in cube:
        pivot = cube.get("pivot", [ox + sx / 2, oy + sy / 2, oz + sz / 2])
        corners = [rotate(point, pivot, cube["rotation"]) for point in corners]
    if "rotation" in bone:
        corners = [rotate(point, bone["pivot"], bone["rotation"]) for point in corners]
    base = texture_color(texture, cube.get("uv", [0, 0]))
    faces = ((0, 1, 2, 3), (4, 7, 6, 5), (0, 4, 5, 1), (3, 2, 6, 7), (1, 5, 6, 2), (0, 3, 7, 4))
    return [([corners[index] for index in face], base) for face in faces]


def render(name: str, yaw: float, pitch: float) -> None:
    data = json.loads(GEOMETRY.read_text(encoding="utf-8"))["minecraft:geometry"][0]
    texture = Image.open(TEXTURE).convert("RGBA")
    faces = []
    for bone in data["bones"]:
        for cube in bone.get("cubes", []):
            faces.extend(cube_faces(cube, bone, texture))

    yaw_radians = math.radians(yaw)
    pitch_radians = math.radians(pitch)

    def view(point: tuple[float, float, float]) -> tuple[float, float, float]:
        x, y, z = point
        x, z = x * math.cos(yaw_radians) - z * math.sin(yaw_radians), x * math.sin(yaw_radians) + z * math.cos(yaw_radians)
        y, z = y * math.cos(pitch_radians) - z * math.sin(pitch_radians), y * math.sin(pitch_radians) + z * math.cos(pitch_radians)
        return x, y, z

    transformed = [([view(point) for point in face], color) for face, color in faces]
    transformed.sort(key=lambda entry: sum(point[2] for point in entry[0]) / len(entry[0]))
    image = Image.new("RGBA", (512, 512), (17, 23, 25, 255))
    draw = ImageDraw.Draw(image)
    scale = 22.0
    center_x = 256
    baseline = 440
    for face, base in transformed:
        a, b, c = face[0], face[1], face[2]
        ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
        vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
        normal_z = ux * vy - uy * vx
        if normal_z >= 0:
            continue
        depth = sum(point[2] for point in face) / len(face)
        shade = max(0.58, min(1.14, 0.88 - depth * 0.015))
        color = tuple(max(0, min(255, round(channel * shade))) for channel in base) + (255,)
        polygon = [(center_x + point[0] * scale, baseline - point[1] * scale) for point in face]
        draw.polygon(polygon, fill=color, outline=(226, 213, 147, 185))

    draw.text((18, 18), f"straw_doll - {name}", fill=(207, 244, 242, 255))
    OUTPUT.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT / f"straw_doll_{name}.png")


if __name__ == "__main__":
    render("front", 0, 0)
    render("side", 90, 0)
    render("three_quarter", -35, 12)
