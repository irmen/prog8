#!/usr/bin/env python3
"""
IZX0 Image File Format Specification & Exporter Script
======================================================

Overview:
---------
The IZX0 format is a planar image format designed for Amiga OCS/ECS/AGA hardware.
It stores image metadata, a hardware-compatible color palette, and individually 
compressed bitplane streams using the ZX0 compression algorithm (via Salvador).

File Layout:
------------
+-----------------------------------------------------------------------------+
| Fixed Binary Header (52 Bytes, Big-Endian)                                  |
+-----------------------------------------------------------------------------+
| Palette Data (Variable length = palette_data_size)                          |
|   - ECS Mode (is_aga=0): Array of UWORD ($0RGB, 2 bytes/color)              |
|   - AGA Mode (is_aga=1): Array of ULONG (0x00RRGGBB, 4 bytes/color)         |
+-----------------------------------------------------------------------------+
| Concatenated Bitplane ZX0 Streams                                           |
|   - Plane 0 Stream (Size = plane_sizes[0])                                  |
|   - Plane 1 Stream (Size = plane_sizes[1])                                  |
|   - ...                                                                     |
|   - Plane N Stream (Size = plane_sizes[N-1])                                |
+-----------------------------------------------------------------------------+

Fixed Binary Header Memory Map (52 Bytes Total):
------------------------------------------------
Offset (Hex)  Offset (Dec)  Type       Description
-------------------------------------------------------------------------------
0x00          0             4 Bytes    Magic Identifier ('IZX0')
0x04          4             UWORD      16-bit Word-aligned Width (e.g., 320)
0x06          6             UWORD      Height in pixels (e.g., 256)
0x08          8             UWORD      Number of bitplanes (1..8)
0x0A          10            UBYTE      is_aga Flag (0 = ECS, 1 = AGA)
0x0B          11            UBYTE      Reserved / Padding Byte (0x00)
0x0C          12            UWORD      Number of Palette Entries (2^nPlanes)
0x0E          14            UWORD      Palette Data Size in Bytes
0x10          16            ULONG[8]   Array of Compressed Sizes (Planes 0..7)
0x30          48            ULONG      Total compressed bitmap data size
-------------------------------------------------------------------------------
Total Header Size: 52 Bytes (0x34)

Palette Entry Specifications:
-----------------------------
1. ECS Mode (is_aga = 0):
   Each entry is a 16-bit UWORD in $0RGB format (Accurate 8-to-4 bit rounding):
   - Bits 15-12 : Unused (0)
   - Bits 11-8  : Red (4-bit, 0x0..0xF)
   - Bits 7-4   : Green (4-bit, 0x0..0xF)
   - Bits 3-0   : Blue (4-bit, 0x0..0xF)
   Total Size = num_palette_entries * 2 bytes.

2. AGA Mode (is_aga = 1):
   Each entry is a 32-bit ULONG in 0x00RRGGBB (ARGB) format:
   - Bits 31-24 : Unused / Alpha (0x00)
   - Bits 23-16 : Red (8-bit, 0x00..0xFF)
   - Bits 15-8  : Green (8-bit, 0x00..0xFF)
   - Bits 7-0   : Blue (8-bit, 0x00..0xFF)
   Total Size = num_palette_entries * 4 bytes.
"""

import argparse
import os
import struct
import subprocess
import sys
from PIL import Image


def compress_zx0(data_bytes):
    """Compresses raw bytes using salvador executable (ZX0 format)."""
    temp_raw = "_temp_plane.raw"
    temp_zx0 = "_temp_plane.zx0"

    with open(temp_raw, "wb") as f:
        f.write(data_bytes)

    try:
        subprocess.run(
            ["salvador", temp_raw, temp_zx0],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        with open(temp_zx0, "rb") as f:
            compressed_data = f.read()
    finally:
        if os.path.exists(temp_raw):
            os.remove(temp_raw)
        if os.path.exists(temp_zx0):
            os.remove(temp_zx0)

    return compressed_data


def channel_8to4(color: int) -> int:
    """Accurate conversion of a single 8-bit color channel value to 4 bits."""
    return (color * 15 + 135) >> 8


def sort_palette_dark_to_light(img):
    """Sort a paletted image by luminance and move black to palette index 0."""
    palette = img.getpalette() or []
    _, max_value = img.getextrema()
    colors = []
    for index in range(max_value + 1):
        offset = index * 3
        rgb = palette[offset : offset + 3]
        colors.append(tuple((rgb + [0, 0, 0])[:3]))

    def sort_key(index):
        r, g, b = colors[index]
        luminance = 299 * r + 587 * g + 114 * b
        return (0 if (r, g, b) == (0, 0, 0) else 1, luminance, r, g, b)

    order = sorted(range(len(colors)), key=sort_key)
    remap = [0] * len(colors)
    for new_index, old_index in enumerate(order):
        remap[old_index] = new_index

    pixels = bytes(remap[index] for index in img.tobytes())
    sorted_img = Image.frombytes("P", img.size, pixels)
    sorted_palette = [component for index in order for component in colors[index]]
    sorted_palette.extend([0] * (768 - len(sorted_palette)))
    sorted_img.putpalette(sorted_palette)
    return sorted_img


def build_ecs_palette(palette_rgb, num_colors):
    """Builds a flat UWORD array suitable for LoadRGB4(ViewPort, table, count).

    Format: $0RGB (16 bits per color, accurately converted using channel_8to4)
    """
    ecs_table = bytearray()

    for i in range(num_colors):
        r = channel_8to4(palette_rgb[i * 3])
        g = channel_8to4(palette_rgb[i * 3 + 1])
        b = channel_8to4(palette_rgb[i * 3 + 2])

        rgb12 = ((r & 0x0F) << 8) | ((g & 0x0F) << 4) | (b & 0x0F)
        ecs_table.extend(struct.pack(">H", rgb12))

    return bytes(ecs_table)


def build_aga_palette(palette_rgb, num_colors):
    """Builds a flat ULONG array of ARGB 32-bit colors (0x00RRGGBB).

    Format: 32 bits per color (Alpha=0x00, Red=8bit, Green=8bit, Blue=8bit)
    """
    aga_table = bytearray()

    for i in range(num_colors):
        r8 = palette_rgb[i * 3]
        g8 = palette_rgb[i * 3 + 1]
        b8 = palette_rgb[i * 3 + 2]

        argb32 = (r8 << 16) | (g8 << 8) | b8
        aga_table.extend(struct.pack(">I", argb32))

    return bytes(aga_table)


def export_izx0(input_path, output_path, force_aga=False, max_colors=256):
    img = Image.open(input_path)

    # 1. Quantize image if necessary
    if img.mode != "P":
        if img.mode in ("RGBA", "LA") or (
            img.mode == "P" and "transparency" in img.info
        ):
            background = Image.new("RGB", img.size, (255, 255, 255))
            if img.mode != "RGBA":
                img = img.convert("RGBA")
            background.paste(img, mask=img.split()[3])
            img = background

        img = img.quantize(
            colors=max_colors,
            method=Image.Quantize.MEDIANCUT,
            dither=Image.Dither.FLOYDSTEINBERG,
        )

    img = sort_palette_dark_to_light(img)

    width, height = img.size

    # Calculate 16-bit word-aligned width
    words_per_row = (width + 15) // 16
    aligned_width = words_per_row * 16

    # 2. Determine required bitplanes
    _, max_val = img.getextrema()
    num_planes = max_val.bit_length() if max_val > 0 else 1

    # Auto-detect AGA if > 5 bitplanes or command line flag passed
    is_aga = 1 if (force_aga or num_planes > 5) else 0
    mode_str = "AGA (32-bit ARGB)" if is_aga else "ECS (16-bit $0RGB)"

    print(
        f"Image: {width}x{height} (Aligned: {aligned_width}x{height}), Planes: {num_planes} [{mode_str}]"
    )

    pixel_bytes = img.tobytes()
    compressed_planes = []

    # 3. Process and compress bitplanes individually
    for plane in range(num_planes):
        plane_mask = 1 << plane
        raw_bytes = bytearray()

        for y in range(height):
            row_start = y * width
            row_pixels = pixel_bytes[row_start : row_start + width]

            current_byte = 0
            bit_count = 0

            for pixel in row_pixels:
                bit = 1 if (pixel & plane_mask) else 0
                current_byte = (current_byte << 1) | bit
                bit_count += 1

                if bit_count == 8:
                    raw_bytes.append(current_byte)
                    current_byte = 0
                    bit_count = 0

            if bit_count > 0:
                current_byte <<= 8 - bit_count
                raw_bytes.append(current_byte)

            # Pad scanline to 16-bit word boundary
            if len(raw_bytes) % 2 != 0:
                raw_bytes.append(0)

        c_data = compress_zx0(bytes(raw_bytes))
        compressed_planes.append(c_data)
        print(f"  Plane {plane}: {len(raw_bytes)} -> {len(c_data)} bytes")

    # 4. Extract Palette Data
    num_palette_entries = 1 << num_planes
    raw_palette_rgb = img.getpalette()[: num_palette_entries * 3]
    raw_palette_rgb += [0] * (num_palette_entries * 3 - len(raw_palette_rgb))

    if is_aga:
        palette_bytes = build_aga_palette(raw_palette_rgb, num_palette_entries)
    else:
        palette_bytes = build_ecs_palette(raw_palette_rgb, num_palette_entries)

    palette_data_size = len(palette_bytes)

    # 5. Build Fixed 52-Byte Binary Header (Big-Endian)
    plane_sizes = [0] * 8
    for i in range(num_planes):
        plane_sizes[i] = len(compressed_planes[i])
    compressed_payload_size = sum(plane_sizes)

    header = struct.pack(
        ">4sHHHBB HH 9I",
        b"IZX0",
        aligned_width,
        height,
        num_planes,
        is_aga,  # 1 = AGA, 0 = ECS
        0,  # Reserved byte
        num_palette_entries,  # 2^num_planes
        palette_data_size,  # Byte count of palette block
        *plane_sizes,
        compressed_payload_size,
    )

    # 6. Write Output File
    with open(output_path, "wb") as f:
        f.write(header)
        f.write(palette_bytes)
        for c_data in compressed_planes:
            f.write(c_data)

    file_size = os.path.getsize(output_path)
    print(f"\nSaved '{output_path}' ({file_size} bytes total)")
    print(
        f"Header: 52B | Palette: {palette_data_size}B ({num_palette_entries} entries) | Streams: {compressed_payload_size}B"
    )


def main():
    parser = argparse.ArgumentParser(
        prog="amiga-export-izx0",
        description="Convert image files (PNG/BMP/etc.) into the IZX0 planar compressed Amiga format.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    # Positional Arguments
    parser.add_argument(
        "input",
        type=str,
        help="Path to source image file",
    )
    parser.add_argument(
        "output",
        type=str,
        help="Path for target IZX0 destination file",
    )

    # Optional Arguments
    parser.add_argument(
        "-aga",
        "--aga",
        action="store_true",
        help="Force 32-bit ARGB palette mode (automatically enabled if bitplanes > 5)",
    )
    parser.add_argument(
        "-c",
        "--max-colors",
        type=int,
        default=256,
        choices=range(2, 257),
        metavar="[2-256]",
        help="Max colors for quantization palette reduction (default: 256)",
    )

    if len(sys.argv) == 1:
        parser.print_help()
        return

    args = parser.parse_args()

    if not os.path.exists(args.input):
        parser.error(f"Input image file '{args.input}' does not exist.")

    try:
        export_izx0(
            input_path=args.input,
            output_path=args.output,
            force_aga=args.aga,
            max_colors=args.max_colors,
        )
    except Exception as e:
        print(f"Error during export: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
