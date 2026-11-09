#!/usr/bin/env python3
"""Bake GemRender's tileable billow/erosion/heat/detail noise using only the standard library."""

import math
from pathlib import Path
import random
import struct
import zlib

SIZE = 64
TILE = SIZE + 2
WIDTH = TILE * 8
OUTPUT = Path(__file__).resolve().parents[1] / "src/main/resources/assets/gemrender/textures/fx/volume_noise.png"


def lattice(frequency, channels, seed):
    rng = random.Random(seed)
    return [tuple(rng.random() for _ in range(channels)) for _ in range(frequency ** 3)]


def value(points, frequency, x, y, z):
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    tx, ty, tz = (v - math.floor(v) for v in (x, y, z))
    tx, ty, tz = (v * v * (3 - 2 * v) for v in (tx, ty, tz))
    result = 0.0
    for dz, wz in ((0, 1 - tz), (1, tz)):
        for dy, wy in ((0, 1 - ty), (1, ty)):
            for dx, wx in ((0, 1 - tx), (1, tx)):
                index = (((iz + dz) % frequency) * frequency + (iy + dy) % frequency) * frequency + (ix + dx) % frequency
                result += points[index][0] * wx * wy * wz
    return result


def worley(points, frequency, x, y, z):
    ix, iy, iz = math.floor(x), math.floor(y), math.floor(z)
    nearest = 4.0
    for dz in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                index = (((iz + dz) % frequency) * frequency + (iy + dy) % frequency) * frequency + (ix + dx) % frequency
                px, py, pz = points[index]
                distance = (ix + dx + px - x) ** 2 + (iy + dy + py - y) ** 2 + (iz + dz + pz - z) ** 2
                nearest = min(nearest, distance)
    return max(0.0, 1.0 - math.sqrt(nearest) / math.sqrt(3))


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))


def generate():
    cells = {f: lattice(f, 3, 8171 + f) for f in (4, 8, 16)}
    values = {f: lattice(f, 1, 2903 + f) for f in (4, 8, 16)}
    # Periodic lattice lookup must work across both positive and negative volume boundaries.
    for f in cells:
        for sample, points in ((worley, cells[f]), (value, values[f])):
            assert math.isclose(sample(points, f, 0.37, 1.19, -0.43),
                                sample(points, f, 0.37 + f, 1.19 - f, -0.43 + f), abs_tol=1e-12)

    voxels = bytearray(SIZE ** 3 * 4)
    for z in range(SIZE):
        for y in range(SIZE):
            for x in range(SIZE):
                coords = [(x + 0.5) / SIZE, (y + 0.5) / SIZE, (z + 0.5) / SIZE]
                w = [worley(cells[f], f, *(v * f for v in coords)) for f in cells]
                n = [value(values[f], f, *(v * f for v in coords)) for f in values]
                channels = (w[0] * 0.65 + n[0] * 0.35,
                            1.0 - (w[1] * 0.65 + w[2] * 0.35),
                            n[0] * 0.55 + n[1] * 0.3 + n[2] * 0.15,
                            w[2])
                at = ((z * SIZE + y) * SIZE + x) * 4
                voxels[at:at + 4] = bytes(round(max(0, min(1, c)) * 255) for c in channels)

    pixels = bytearray(WIDTH * WIDTH * 4)
    for z in range(SIZE):
        for y in range(TILE):
            for x in range(TILE):
                source = ((z * SIZE + (y - 1) % SIZE) * SIZE + (x - 1) % SIZE) * 4
                target = (((z // 8) * TILE + y) * WIDTH + (z % 8) * TILE + x) * 4
                pixels[target:target + 4] = voxels[source:source + 4]
    for c in range(4):
        assert max(voxels[c::4]) - min(voxels[c::4]) > 64
    rows = b"".join(b"\0" + pixels[y * WIDTH * 4:(y + 1) * WIDTH * 4] for y in range(WIDTH))
    image = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", WIDTH, WIDTH, 8, 6, 0, 0, 0))
             + chunk(b"IDAT", zlib.compress(rows, 9)) + chunk(b"IEND", b""))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(image)
    print(f"Wrote {OUTPUT} ({WIDTH}x{WIDTH}, {len(image)} bytes)")


if __name__ == "__main__":
    generate()
