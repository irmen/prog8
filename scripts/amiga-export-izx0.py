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
| Fixed Binary Header (60 Bytes, Big-Endian)                                  |
+-----------------------------------------------------------------------------+
| Palette Data (Variable length = palette_data_size)                          |
|   - ECS Mode (is_aga=0): Array of UWORD ($0RGB, 2 bytes/color)              |
|   - AGA Mode (is_aga=1): Array of ULONG (0x00RRGGBB, 4 bytes/color)         |
|     Packed storage; expand at load time into a LoadRGB32 table (e.g. via     |
|     the graphics.load_palette library routine), LoadRGB32 does not accept    |
|     packed 0x00RRGGBB directly                                               |
|   - Absent (num entries = 0): palette is supplied externally                |
+-----------------------------------------------------------------------------+
| Concatenated Bitplane ZX0 Streams                                           |
|   - Plane 1 Stream (Size = plane_sizes[0])                                  |
|   - Plane 2 Stream (Size = plane_sizes[1])                                  |
|   - ...                                                                     |
|   - Plane N Stream (Size = plane_sizes[N-1])                                |
|   - Optional cookie-cut mask stream (Size = plane_sizes[8],                |
|     1 bit per pixel, bit 1 = pixel visible)                                |
+-----------------------------------------------------------------------------+

Fixed Binary Header Memory Map (60 Bytes Total):
------------------------------------------------
Offset (Hex)  Offset (Dec)  Type       Description
-------------------------------------------------------------------------------
0x00          0             4 Bytes    Magic Identifier ('IZX0')
0x04          4             UWORD      Actual image width in pixels (scanlines are
                                      padded to a 16-bit word boundary, so the
                                      in-memory stride is ceil(width/16)*2 bytes)
0x06          6             UWORD      Height in pixels (e.g., 256)
0x08          8             SWORD      Origin X (hotspot/anchor, default 0)
0x0A          10            SWORD      Origin Y (hotspot/anchor, default 0)
0x0C          12            UWORD      Number of color bitplanes (1..8)
0x0E          14            UBYTE      is_aga Flag (0 = ECS, 1 = AGA)
0x0F          15            UBYTE      Reserved, must be 0
0x10          16            UWORD      Number of Palette Entries (2^num_planes,
                                      or 0 = palette supplied externally)
0x12          18            UWORD      Palette Data Size in Bytes (0 if omitted)
0x14          20            ULONG      Total compressed plane-stream payload size
                                      (sum of the streams below; excludes header
                                      and palette)
0x18          24            ULONG[9]   Array of Compressed Sizes (color planes
                                      in indices 0..7, optional mask in index 8;
                                      a nonzero mask size means a mask stream
                                      follows the color plane streams)
-------------------------------------------------------------------------------
Total Header Size: 60 Bytes (0x3C)

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
   Each entry is a 32-bit ULONG in 0x00RRGGBB format (compact packed storage).
   This is NOT directly usable with graphics.library LoadRGB32, which takes an
   expanded table (count/start header plus 3 ULONGs per color, intensity in the
   top byte - see the graphics.load_palette library routine that performs this
   expansion). A 256-color palette is 1024 bytes.
"""

import argparse
import io
import os
import struct
import subprocess
import sys
from PIL import Image

try:
    from cx16images import BAYER_4X4, BAYER_8X8, apply_ordered_dither
except ImportError:  # script is run from another directory; add its own dir to the path
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from cx16images import BAYER_4X4, BAYER_8X8, apply_ordered_dither


def compress_zx0(data_bytes):
    """Compresses raw bytes using salvador executable (ZX0 format)."""
    import tempfile
    with tempfile.NamedTemporaryFile(prefix="izx0_plane_", suffix=".raw", delete=False) as f:
        temp_raw = f.name
        f.write(data_bytes)
    temp_zx0 = temp_raw + ".zx0"

    try:
        subprocess.run(
            ["salvador", temp_raw, temp_zx0],
            check=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        with open(temp_zx0, "rb") as f:
            compressed_data = f.read()
    except FileNotFoundError:
        raise SystemExit("error: 'salvador' executable not found on PATH (needed for ZX0 compression)")
    finally:
        if os.path.exists(temp_raw):
            os.remove(temp_raw)
        if os.path.exists(temp_zx0):
            os.remove(temp_zx0)

    return compressed_data


class _Zx0BitReader:
    """MSB-first bit reader with independent byte-aligned reads (mirrors salvador)."""

    def __init__(self, data):
        self._data = data
        self._pos = 0
        self._bits = 0
        self._mask = 0

    def read_bit(self):
        if self._mask == 0:
            if self._pos >= len(self._data):
                return -1
            self._bits = self._data[self._pos]
            self._pos += 1
            self._mask = 128
        bit = 1 if self._bits & 128 else 0
        self._bits = (self._bits << 1) & 0xFF
        self._mask >>= 1
        return bit

    def read_byte(self):
        if self._pos >= len(self._data):
            return -1
        byte = self._data[self._pos]
        self._pos += 1
        return byte


def _zx0_read_elias(reader):
    value = 1
    while True:
        bit = reader.read_bit()
        if bit != 0:
            if bit < 0:
                raise ValueError("truncated ZX0 stream in Elias code")
            return value
        data = reader.read_bit()
        if data < 0:
            raise ValueError("truncated ZX0 stream in Elias code")
        value = (value << 1) | data


def _zx0_read_elias_prefix(reader, first_bit):
    # First length bit comes from the offset LSB; a set bit means length 1.
    if first_bit != 0:
        return 1
    value = (1 << 1) | reader.read_bit()
    while True:
        bit = reader.read_bit()
        if bit != 0:
            if bit < 0:
                raise ValueError("truncated ZX0 stream in Elias code")
            return value
        data = reader.read_bit()
        if data < 0:
            raise ValueError("truncated ZX0 stream in Elias code")
        value = (value << 1) | data


def _zx0_read_offset_msb(reader):
    # Offset high byte accumulates from $FE (see unZX0 in
    # compiler/res/prog8lib/shared_m68k_compression.p8); it is stored
    # incremented, so $FF marks end-of-data.
    value = 0xFE
    while True:
        bit = reader.read_bit()
        if bit < 0:
            raise ValueError("truncated ZX0 stream in offset")
        if bit != 0:
            value = (value + 1) & 0xFF
            return None if value == 0 else value
        data = reader.read_bit()
        if data < 0:
            raise ValueError("truncated ZX0 stream in offset")
        value = ((value << 1) | data) & 0xFF


def zx0_decompress(data):
    """Decompress ZX0 data, as produced by salvador and decoded by unZX0.

    Faithful transliteration of the 68000 unZX0 routine in
    compiler/res/prog8lib/shared_m68k_compression.p8 (Einar Saukas' format).
    """
    reader = _Zx0BitReader(data)
    out = bytearray()
    offset = 1
    first = True
    while True:
        if first:
            first = False
            is_match = 0
        else:
            is_match = reader.read_bit()
            if is_match < 0:
                raise ValueError("truncated ZX0 stream")
        if is_match == 0:
            for _ in range(_zx0_read_elias(reader)):
                byte = reader.read_byte()
                if byte < 0:
                    raise ValueError("truncated ZX0 literals")
                out.append(byte)
            is_match = reader.read_bit()
            if is_match < 0:
                raise ValueError("truncated ZX0 stream")
        if is_match:
            high = _zx0_read_offset_msb(reader)
            if high is None:
                break
            low = reader.read_byte()
            if low < 0:
                raise ValueError("truncated ZX0 offset")
            # Negative 16-bit offset halved: the stored low byte's bit 0 is
            # a length bit, the remaining 15 bits are the offset magnitude.
            # (Arithmetic shift, like the 68k asr.l: floor for negatives.)
            offset = -((((high << 8) | low) - 0x10000) >> 1)
            length = _zx0_read_elias_prefix(reader, low & 1) + 1
        else:
            length = _zx0_read_elias(reader)
        if offset > len(out):
            raise ValueError("invalid ZX0 offset")
        for _ in range(length):
            out.append(out[len(out) - offset])
    return bytes(out)


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


def read_iff_via_convert(input_path):
    """Decode an IFF ILBM file via ImageMagick, or return None if not IFF.

    Pillow cannot read IFF ILBM, so IFF input (magic FORM....ILBM/PBM) is
    piped through `convert` to an in-memory PNG. The BytesIO buffer stays
    alive via the image's file handle, so no temp files are needed.
    """
    with open(input_path, "rb") as f:
        header = f.read(12)
    if len(header) < 12 or header[:4] != b"FORM" or header[8:12] not in (b"ILBM", b"PBM "):
        return None
    try:
        proc = subprocess.run(
            ["convert", input_path, "png:-"],
            check=True,
            capture_output=True,
        )
    except FileNotFoundError:
        raise RuntimeError(
            "IFF ILBM input requires the 'convert' tool (ImageMagick), which was not found"
        )
    except subprocess.CalledProcessError as e:
        detail = e.stderr.decode(errors="replace").strip() if e.stderr else ""
        raise RuntimeError(f"'convert' failed on IFF input '{input_path}': {detail}")
    return Image.open(io.BytesIO(proc.stdout))


def decode_izx0(input_path):
    """Decode an IZX0 file back into a PIL image (RGB, or RGBA when a mask plane is present)."""
    with open(input_path, "rb") as f:
        data = f.read()
    if len(data) < 60 or data[:4] != b"IZX0":
        raise ValueError(f"not an IZX0 file: {input_path}")
    (magic, width, height, origin_x, origin_y, num_planes, is_aga, reserved,
     num_entries, palette_size, payload_size, *plane_sizes) = struct.unpack(">4sHHhhHBBHHI9I", data[:60])
    header_size = 60
    if reserved != 0:
        raise ValueError(f"invalid IZX0 header in {input_path}: reserved byte is {reserved:#04x}, must be 0")
    has_mask = plane_sizes[8] != 0
    color_planes = num_planes
    if width < 1 or height < 1:
        raise ValueError(f"invalid IZX0 dimensions in {input_path}: {width}x{height}")
    # Scanlines are padded to a 16-bit word boundary.
    raw_stride = ((width + 15) // 16) * 2
    if not 1 <= num_planes <= 8 or color_planes < 1:
        raise ValueError(f"invalid IZX0 header in {input_path}")
    if num_entries != (1 << color_planes) and num_entries != 0:
        raise ValueError(f"invalid IZX0 header in {input_path}")
    if num_entries == 0 and palette_size != 0:
        raise ValueError(f"invalid IZX0 header in {input_path}: palette entries is 0 but palette size is {palette_size}")
    if header_size + palette_size + payload_size != len(data):
        raise ValueError(f"size mismatch in {input_path}")

    offset = header_size + palette_size
    raw_planes = []
    for i in range(num_planes):
        comp = data[offset : offset + plane_sizes[i]]
        offset += plane_sizes[i]
        raw = zx0_decompress(comp)
        if len(raw) != height * raw_stride:
            raise ValueError(f"plane {i + 1} decompresses to {len(raw)} bytes, expected {height * raw_stride}")
        raw_planes.append(raw)
    if has_mask:
        comp = data[offset : offset + plane_sizes[8]]
        raw_mask = zx0_decompress(comp)
        if len(raw_mask) != height * raw_stride:
            raise ValueError(f"mask plane decompresses to {len(raw_mask)} bytes, expected {height * raw_stride}")

    pixels = bytearray(width * height)
    mask = bytearray(width * height)
    for y in range(height):
        for x in range(width):
            byte = x // 8
            bit = 7 - (x % 8)
            index = 0
            for plane in range(color_planes):
                if raw_planes[plane][y * raw_stride + byte] & (1 << bit):
                    index |= 1 << plane
            pixels[y * width + x] = index
            if has_mask:
                if raw_mask[y * raw_stride + byte] & (1 << bit):
                    mask[y * width + x] = 255

    palette = data[header_size : header_size + palette_size]
    rgb = bytearray()
    if not palette:
        # No palette stored (supplied externally): synthesize a grayscale ramp so --display still shows image structure.
        levels = (1 << color_planes) - 1
        for i in range(1 << color_planes):
            g = i * 255 // levels if levels else 0
            rgb.extend((g, g, g))
    elif is_aga:
        for (color,) in struct.iter_unpack(">I", palette):
            rgb.extend(((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF))
    else:
        for (color,) in struct.iter_unpack(">H", palette):
            rgb.extend((((color >> 8) & 0xF) * 255 // 15,
                        ((color >> 4) & 0xF) * 255 // 15,
                        (color & 0xF) * 255 // 15))
    rgb.extend([0] * (768 - len(rgb)))

    img = Image.frombytes("P", (width, height), bytes(pixels))
    img.putpalette(list(rgb))
    if has_mask:
        # Masked-out pixels become transparent (mask bit 1 = visible).
        alpha = Image.frombytes("L", (width, height), bytes(mask))
        img = img.convert("RGBA")
        img.putalpha(alpha)
        return img
    return img.convert("RGB")


def show_izx0_info(input_path):
    """Parse an existing IZX0 file and display its header/palette metadata."""
    with open(input_path, "rb") as f:
        data = f.read()

    if len(data) < 60:
        print(f"Error: '{input_path}' is too small to be an IZX0 file.", file=sys.stderr)
        sys.exit(1)

    (magic, width, height, origin_x, origin_y, num_planes, is_aga, reserved,
     num_entries, palette_size, payload_size, *plane_sizes) = struct.unpack(">4sHHhhHBBHHI9I", data[:60])
    header_size = 60
    has_mask = plane_sizes[8] != 0
    color_planes = num_planes

    errors = []
    if magic != b"IZX0":
        errors.append(f"bad magic {magic!r} (expected b'IZX0')")
    if not 1 <= num_planes <= 8:
        errors.append(f"bitplane count {num_planes} out of range 1..8")
    if reserved != 0:
        errors.append(f"reserved byte is {reserved:#04x}, must be 0")
    if num_entries != 0 and num_entries != (1 << color_planes) and 1 <= num_planes <= 8:
        errors.append(f"palette entries {num_entries} != 2^{color_planes}")
    expected_palette = num_entries * (4 if is_aga else 2)
    if palette_size != expected_palette:
        errors.append(f"palette size {palette_size} != {expected_palette} for {num_entries} entries")
    if sum(plane_sizes[:num_planes]) + (plane_sizes[8] if has_mask else 0) != payload_size:
        errors.append("plane sizes do not add up to the payload size")
    if any(s != 0 for s in plane_sizes[num_planes:8]):
        errors.append("unused plane size slots are not zero")
    if header_size + palette_size + payload_size != len(data):
        errors.append(
            f"file size {len(data)} != {header_size} + {palette_size} + {payload_size} "
            "(header + palette + streams)"
        )
    if is_aga not in (0, 1):
        errors.append(f"is_aga flag is {is_aga}, expected 0 or 1")

    mode_str = "AGA (32-bit ARGB)" if is_aga else "ECS (16-bit $0RGB)"
    if errors:
        print(f"File: {input_path} ({len(data)} bytes)")
        print(f"Magic: {magic!r}")
        print("Invalid IZX0 file:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        sys.exit(1)

    print(f"File: {input_path} ({len(data)} bytes)")
    print(f"Magic: {magic!r}")
    print(f"Dimensions: {width}x{height} (stride {((width + 15) // 16) * 2} bytes/row)")
    if has_mask:
        print(f"Bitplanes: {num_planes} [{mode_str}] + cookie-cut mask")
    else:
        print(f"Bitplanes: {num_planes} [{mode_str}]")
    print(f"Origin: ({origin_x}, {origin_y})")
    if num_entries == 0:
        print("Palette entries: 0 (omitted, palette supplied externally)")
    else:
        print(f"Palette entries: {num_entries} ({palette_size} bytes)")
    if errors:
        print("Invalid IZX0 file:", file=sys.stderr)
        for e in errors:
            print(f"  - {e}", file=sys.stderr)
        sys.exit(1)


def export_izx0(input_path, output_path, force_aga=False, max_colors=256, dither="none", no_palette=False,
               origin=(0, 0), mask_from_alpha=False):
    img = read_iff_via_convert(input_path)
    if img is None:
        img = Image.open(input_path)

    # Capture the alpha channel as the cookie-cut mask before compositing
    # (mask bit 1 = pixel visible).
    mask_pixels = None
    if mask_from_alpha:
        if img.mode in ("RGBA", "LA"):
            alpha = img.split()[-1]
            mask_pixels = [1 if a >= 128 else 0 for a in
                           (alpha.get_flattened_data() if hasattr(alpha, "get_flattened_data") else alpha.getdata())]
        elif img.mode == "P" and "transparency" in img.info:
            alpha = img.info["transparency"]
            if isinstance(alpha, bytes):
                mask_pixels = [1 if a >= 128 else 0 for a in alpha[: img.size[0] * img.size[1]]]
            else:
                mask_pixels = [1 if i != alpha else 0 for i in
                               (img.get_flattened_data() if hasattr(img, "get_flattened_data") else img.getdata())]
        else:
            print("warning: --mask-from-alpha given but image has no alpha channel; no mask stored",
                  file=sys.stderr)

    # 1. Quantize image if necessary
    if img.mode != "P":
        if img.mode in ("RGBA", "LA") or (
            img.mode == "P" and "transparency" in img.info
        ):
            # Opaque background for compositing: black when the alpha channel
            # doubles as the mask (avoids white fringes), white otherwise.
            background = Image.new("RGB", img.size, (0, 0, 0) if mask_pixels else (255, 255, 255))
            if img.mode != "RGBA":
                img = img.convert("RGBA")
            background.paste(img, mask=img.split()[3])
            img = background

        if dither == "fs":
            # Pillow ignores dither in quantize() unless a palette is passed
            # (https://github.com/python-pillow/Pillow/issues/5836): derive the
            # palette first, then quantize into it with dithering enabled.
            palette_img = img.quantize(
                colors=max_colors,
                method=Image.Quantize.MEDIANCUT,
            )
            img = img.quantize(
                colors=max_colors,
                method=Image.Quantize.MEDIANCUT,
                palette=palette_img,
                dither=Image.Dither.FLOYDSTEINBERG,
            )
        elif dither in ("ordered4", "ordered8"):
            matrix = BAYER_4X4 if dither == "ordered4" else BAYER_8X8
            palette_img = img.quantize(
                colors=max_colors,
                method=Image.Quantize.MEDIANCUT,
            )
            img = apply_ordered_dither(img, palette_img, matrix)
        else:
            img = img.quantize(
                colors=max_colors,
                method=Image.Quantize.MEDIANCUT,
                dither=Image.Dither.NONE,
            )

    img = sort_palette_dark_to_light(img)

    width, height = img.size

    # Scanlines are padded to a 16-bit word boundary in the plane streams.

    # 2. Determine required bitplanes (color planes; mask appended later)
    _, max_val = img.getextrema()
    color_planes = max_val.bit_length() if max_val > 0 else 1

    # Auto-detect AGA if > 5 color bitplanes or command line flag passed
    is_aga = 1 if (force_aga or color_planes > 5) else 0
    mode_str = "AGA (32-bit ARGB)" if is_aga else "ECS (16-bit $0RGB)"

    print(
        f"Image: {width}x{height} (stride {((width + 15) // 16) * 2} bytes/row), "
        f"Planes: {color_planes} [{mode_str}]"
    )

    pixel_bytes = img.tobytes()
    compressed_planes = []

    # 3. Process and compress bitplanes individually
    for plane in range(color_planes):
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
        print(f"  Plane {plane + 1}: {len(raw_bytes)} -> {len(c_data)} bytes")

    mask_data = None
    if mask_pixels is not None:
        # Compress the cookie-cut mask as its own stream (mask bit 1 = visible).
        raw_bytes = bytearray()
        for y in range(height):
            row_start = y * width
            current_byte = 0
            bit_count = 0
            for bit in mask_pixels[row_start : row_start + width]:
                current_byte = (current_byte << 1) | (1 if bit else 0)
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
        mask_data = compress_zx0(bytes(raw_bytes))
        print(f"  Mask plane: {len(raw_bytes)} -> {len(mask_data)} bytes")

    num_planes = len(compressed_planes)
    if num_planes > 8:
        raise ValueError(f"too many color planes ({num_planes}, max 8); reduce colors")

    # 4. Extract Palette Data (unless omitted: palette supplied externally)
    if no_palette:
        num_palette_entries = 0
        palette_bytes = b""
    else:
        num_palette_entries = 1 << color_planes
        raw_palette_rgb = img.getpalette()[: num_palette_entries * 3]
        raw_palette_rgb += [0] * (num_palette_entries * 3 - len(raw_palette_rgb))

        if is_aga:
            palette_bytes = build_aga_palette(raw_palette_rgb, num_palette_entries)
        else:
            palette_bytes = build_ecs_palette(raw_palette_rgb, num_palette_entries)

    palette_data_size = len(palette_bytes)

    # 5. Build Fixed 60-Byte Binary Header (Big-Endian)
    plane_sizes = [0] * 9
    for i in range(num_planes):
        plane_sizes[i] = len(compressed_planes[i])
    if mask_data is not None:
        plane_sizes[8] = len(mask_data)
    compressed_payload_size = sum(plane_sizes)

    header = struct.pack(
        ">4sHHhhHBBHHI9I",
        b"IZX0",
        width,  # actual pixel width; scanlines padded to 16-bit boundary
        height,
        origin[0],  # hotspot/anchor X
        origin[1],  # hotspot/anchor Y
        num_planes,  # color planes only; mask (if any) lives in plane_sizes[8]
        is_aga,  # 1 = AGA, 0 = ECS
        0,  # Reserved, must be 0
        num_palette_entries,  # 2^num_planes, or 0 if omitted (palette supplied externally)
        palette_data_size,  # Byte count of palette block (0 if omitted)
        compressed_payload_size,  # total compressed plane-stream payload (excludes header, palette)
        *plane_sizes,
    )

    # 6. Write Output File
    with open(output_path, "wb") as f:
        f.write(header)
        f.write(palette_bytes)
        for c_data in compressed_planes:
            f.write(c_data)
        if mask_data is not None:
            f.write(mask_data)

    file_size = os.path.getsize(output_path)
    print(f"\nSaved '{output_path}' ({file_size} bytes total)")
    print(
        f"Header: 60 | Palette: {palette_data_size} ({num_palette_entries} entries) | Streams: {compressed_payload_size}"
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
        nargs="?",
        default=None,
        help="Path for target IZX0 destination file (not needed with --info)",
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
    parser.add_argument(
        "-d",
        "--dither",
        nargs="?",
        const="fs",
        default="none",
        choices=("none", "fs", "ordered4", "ordered8"),
        metavar="MODE",
        help="Dithering when quantizing: none, fs (Floyd-Steinberg), "
        "ordered4 (4x4 Bayer), ordered8 (8x8 Bayer). "
        "Bare --dither means fs. (default: none)",
    )
    parser.add_argument(
        "--no-palette",
        action="store_true",
        help="Omit the palette from the file (0 entries, 0 bytes; "
        "palette is supplied externally, e.g. shared across BOBs)",
    )
    parser.add_argument(
        "--origin",
        nargs=2,
        type=int,
        default=(0, 0),
        metavar=("X", "Y"),
        help="Hotspot/anchor origin stored in the header (default: 0 0)",
    )
    parser.add_argument(
        "--mask-from-alpha",
        action="store_true",
        help="Store the image alpha channel as a cookie-cut mask stream "
        "(size in plane_sizes[8]; mask bit 1 = pixel visible)",
    )
    parser.add_argument(
        "--info",
        action="store_true",
        help="Show the metadata of an existing IZX0 file instead of converting",
    )
    parser.add_argument(
        "--display",
        action="store_true",
        help="Decode an existing IZX0 file and display it instead of converting",
    )

    if len(sys.argv) == 1:
        parser.print_help()
        return

    args = parser.parse_args()

    if not os.path.exists(args.input):
        parser.error(f"Input image file '{args.input}' does not exist.")
    if args.info:
        show_izx0_info(args.input)
        return
    if args.display:
        img = decode_izx0(args.input)
        img = img.resize((img.size[0] * 2, img.size[1] * 2), Image.NEAREST)
        print(f"Displaying {img.size[0]}x{img.size[1]} image from '{args.input}'")
        img.show()
        return
    if not args.output:
        parser.error("Output file is required (or use --info for metadata only).")

    try:
        export_izx0(
            input_path=args.input,
            output_path=args.output,
            force_aga=args.aga,
            max_colors=args.max_colors,
            dither=args.dither,
            no_palette=args.no_palette,
            origin=tuple(args.origin),
            mask_from_alpha=args.mask_from_alpha,
        )
    except Exception as e:
        print(f"Error during export: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
