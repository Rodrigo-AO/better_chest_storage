"""Generates the mod's PNG assets.

Minecraft textures are tiny, so keeping them as readable source beats shipping opaque binaries:
the art below is the source of truth and this script is the (dependency free) compiler that turns
it into PNGs. The item texture is ASCII pixel art; the GUI sheet is drawn from rectangles whose
coordinates mirror the layout constants in ChestGridScreen and ChestGridMenu.

Usage:  python tools/make_textures.py
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
ASSETS = REPO_ROOT / "src" / "main" / "resources" / "assets" / "better_chest_storage" / "textures"
PREVIEWS = REPO_ROOT / "build" / "texture-preview"

Color = tuple[int, int, int, int]

# ---------------------------------------------------------------- item art

# Oak-ish palette, sampled to sit next to vanilla wooden tools without clashing.
PALETTE: dict[str, Color] = {
    ".": (0, 0, 0, 0),           # transparent
    "D": (58, 41, 23, 255),      # outline / deepest shadow
    "S": (104, 74, 41, 255),     # shadow
    "M": (143, 106, 61, 255),    # base wood
    "L": (176, 137, 84, 255),    # lit wood
    "H": (208, 172, 117, 255),   # highlight
}

CHEST_CONNECTOR = [
    "........DDD..DDD",
    "........DHLD.DLD",
    "........DHLD.DLD",
    "........DHLLLLLD",
    "........DHLLLLD.",
    ".......DHLLLLD..",
    ".......DHLMSD...",
    "......DLMSD.....",
    ".....DLMSD......",
    "....DLMSD.......",
    "...DLMSD........",
    "...DLSSD........",
    "..DLSSD.........",
    "..DLMSD.........",
    "..DLMSD.........",
    "..DDDD..........",
]

# ---------------------------------------------------------------- GUI layout
#
# These must stay in step with the constants in ChestGridScreen / ChestGridMenu.

TEXTURE_SIZE = 256
PANEL_WIDTH = 194
PANEL_HEIGHT = 190

SLOT_SIZE = 18
GRID_X, GRID_Y = 8, 20
GRID_COLUMNS, GRID_ROWS = 9, 4

PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y = 8, 106
PLAYER_INVENTORY_ROWS = 3
HOTBAR_Y = 164

SCROLLBAR_X, SCROLLBAR_Y = 172, 20
SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT = 14, GRID_ROWS * SLOT_SIZE

# The scroll knob lives below the panel on the same sheet.
KNOB_X, KNOB_Y = 0, PANEL_HEIGHT + 2
KNOB_WIDTH, KNOB_HEIGHT = 12, 15

# Vanilla GUI palette.
PANEL_FACE: Color = (198, 198, 198, 255)
PANEL_LIGHT: Color = (255, 255, 255, 255)
PANEL_DARK: Color = (85, 85, 85, 255)
INSET_FACE: Color = (139, 139, 139, 255)
INSET_DARK: Color = (55, 55, 55, 255)
KNOB_FACE: Color = (192, 192, 192, 255)
TRANSPARENT: Color = (0, 0, 0, 0)


class Canvas:
    """A tiny RGBA raster with just the primitives the GUI sheet needs."""

    def __init__(self, width: int, height: int, fill: Color = TRANSPARENT) -> None:
        self.width = width
        self.height = height
        self.pixels = [list(fill) for _ in range(width * height)]

    def set(self, x: int, y: int, color: Color) -> None:
        if 0 <= x < self.width and 0 <= y < self.height:
            self.pixels[y * self.width + x] = list(color)

    def rect(self, x: int, y: int, width: int, height: int, color: Color) -> None:
        for dy in range(height):
            for dx in range(width):
                self.set(x + dx, y + dy, color)

    def bevel(self, x: int, y: int, width: int, height: int,
              face: Color, light: Color, dark: Color) -> None:
        """A raised (light top-left) or sunken (dark top-left) vanilla style box."""
        self.rect(x, y, width, height, face)

        for dx in range(width):
            self.set(x + dx, y, light)
            self.set(x + dx, y + height - 1, dark)

        for dy in range(height):
            self.set(x, y + dy, light)
            self.set(x + width - 1, y + dy, dark)

        # Square off the two corners the two edges disagree about.
        self.set(x + width - 1, y, face)
        self.set(x, y + height - 1, face)

    def to_rows(self) -> bytes:
        raw = bytearray()
        for y in range(self.height):
            raw.append(0)  # PNG filter type 0 (None)
            for x in range(self.width):
                raw.extend(self.pixels[y * self.width + x])
        return bytes(raw)


def write_png(path: Path, width: int, height: int, raw: bytes) -> None:
    def chunk(kind: bytes, data: bytes) -> bytes:
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )

    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)
    print(f"wrote {path.relative_to(REPO_ROOT)} ({width}x{height})")


def write_art(path: Path, rows: list[str], scale: int = 1) -> None:
    width = len(rows[0])
    if any(len(row) != width for row in rows):
        raise ValueError(f"{path.name}: all rows must have the same width")

    canvas = Canvas(width * scale, len(rows) * scale)
    for y, row in enumerate(rows):
        for x, char in enumerate(row):
            canvas.rect(x * scale, y * scale, scale, scale, PALETTE[char])

    write_png(path, canvas.width, canvas.height, canvas.to_rows())


def draw_slot(canvas: Canvas, x: int, y: int) -> None:
    """A sunken 18x18 slot, dark on the top-left like every vanilla container."""
    canvas.bevel(x, y, SLOT_SIZE, SLOT_SIZE, INSET_FACE, INSET_DARK, PANEL_LIGHT)


def build_gui() -> Canvas:
    canvas = Canvas(TEXTURE_SIZE, TEXTURE_SIZE)
    canvas.bevel(0, 0, PANEL_WIDTH, PANEL_HEIGHT, PANEL_FACE, PANEL_LIGHT, PANEL_DARK)

    for row in range(GRID_ROWS):
        for column in range(GRID_COLUMNS):
            draw_slot(canvas, GRID_X + column * SLOT_SIZE, GRID_Y + row * SLOT_SIZE)

    for row in range(PLAYER_INVENTORY_ROWS):
        for column in range(GRID_COLUMNS):
            draw_slot(canvas, PLAYER_INVENTORY_X + column * SLOT_SIZE,
                      PLAYER_INVENTORY_Y + row * SLOT_SIZE)

    for column in range(GRID_COLUMNS):
        draw_slot(canvas, PLAYER_INVENTORY_X + column * SLOT_SIZE, HOTBAR_Y)

    canvas.bevel(SCROLLBAR_X, SCROLLBAR_Y, SCROLLBAR_WIDTH, SCROLLBAR_HEIGHT,
                 INSET_FACE, INSET_DARK, PANEL_LIGHT)

    canvas.bevel(KNOB_X, KNOB_Y, KNOB_WIDTH, KNOB_HEIGHT, KNOB_FACE, PANEL_LIGHT, PANEL_DARK)

    return canvas


if __name__ == "__main__":
    write_art(ASSETS / "item" / "chest_connector.png", CHEST_CONNECTOR)
    write_art(PREVIEWS / "chest_connector.png", CHEST_CONNECTOR, scale=16)

    gui = build_gui()
    write_png(ASSETS / "gui" / "chest_grid.png", gui.width, gui.height, gui.to_rows())
