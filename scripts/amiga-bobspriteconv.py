#!/usr/bin/env python3
"""Convert images into raw Amiga BOB or hardware-sprite data and Prog8 includes.

Requirements: Pillow. Dithering and 4-bit color conversion reuse cx16images.py.
Sprite transparency is always output as pen 0. Source alpha, --transparent-color,
or --transparent-index can select which source pixels map to that pen.
"""

import argparse
import re
import struct
from pathlib import Path
from typing import Sequence

from PIL import Image, features

if __package__:
    from .cx16images import (
        BAYER_4X4,
        BAYER_8X8,
        apply_ordered_dither,
        channel_8to4,
    )
else:
    from cx16images import (
        BAYER_4X4,
        BAYER_8X8,
        apply_ordered_dither,
        channel_8to4,
    )

RGB = tuple[int, int, int]
DITHER_MODES = ("none", "fs", "ordered4", "ordered8")


def pixels(image: Image.Image):
    get_pixels = getattr(image, "get_flattened_data", None)
    return list(get_pixels() if get_pixels else image.getdata())


def parse_rgb(value: str) -> RGB:
    value = value.removeprefix("#").removeprefix("$")
    if len(value) != 6:
        raise argparse.ArgumentTypeError("color must be RRGGBB")
    try:
        return tuple(int(value[i : i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]
    except ValueError as error:
        raise argparse.ArgumentTypeError("color must be hexadecimal RRGGBB") from error


def load_source(path: Path, transparent_color: RGB | None, transparent_index: int | None):
    source = Image.open(path)
    width, height = source.size
    if width < 1 or height < 1:
        raise ValueError("image dimensions must be positive")
    if transparent_index is not None and source.mode != "P":
        raise ValueError("--transparent-index requires a paletted (P mode) image")
    source_indices = source.tobytes() if source.mode == "P" else None
    rgba = source.convert("RGBA")
    source_pixels = pixels(rgba)
    rgb_pixels: list[RGB] = []
    visible = bytearray(width * height)
    for i, (r, g, b, a) in enumerate(source_pixels):
        rgb_pixels.append((r, g, b))
        visible[i] = int(a >= 128)
        if transparent_color is not None and (r, g, b) == transparent_color:
            visible[i] = 0
        if transparent_index is not None and source_indices[i] == transparent_index:
            visible[i] = 0
    rgb = Image.new("RGB", (width, height))
    rgb.putdata(rgb_pixels)
    return rgb, visible, width, height


def reduce_to_ocs(rgb: Image.Image, palette_mode: str) -> Image.Image:
    if palette_mode == "aga":
        return rgb
    lut = [channel_8to4(value) * 17 for value in range(256)]
    return rgb.point(lut * 3)


def make_palette(samples: Sequence[RGB], count: int) -> list[RGB]:
    if not samples:
        raise ValueError("image has no visible pixels to convert")
    sample_image = Image.new("RGB", (len(samples), 1))
    sample_image.putdata(samples)
    method = Image.Quantize.MEDIANCUT
    if features.check_feature("libimagequant"):
        method = Image.Quantize.LIBIMAGEQUANT
    indexed = sample_image.quantize(colors=count, method=method, dither=Image.Dither.NONE)
    source_palette = indexed.getpalette() or []
    result: list[RGB] = []
    seen: set[RGB] = set()
    for index in sorted(set(indexed.tobytes())):
        offset = index * 3
        color = tuple(source_palette[offset : offset + 3])
        if len(color) == 3 and color not in seen:
            seen.add(color)
            result.append(color)  # type: ignore[arg-type]
    if not result:
        raise ValueError("could not create a palette from the visible image pixels")
    return result


def quantize_to_palette(rgb: Image.Image, palette: Sequence[RGB], dither: str) -> bytearray:
    if not palette:
        raise ValueError("palette cannot be empty")
    colors = list(palette)
    full_palette = [component for color in colors for component in color]
    last_color = colors[-1]
    full_palette.extend(list(last_color) * (256 - len(colors)))
    palette_image = Image.new("P", (1, 1))
    palette_image.putpalette(full_palette)
    if dither == "ordered4":
        indexed = apply_ordered_dither(rgb, palette_image, BAYER_4X4)
    elif dither == "ordered8":
        indexed = apply_ordered_dither(rgb, palette_image, BAYER_8X8)
    else:
        mode = Image.Dither.FLOYDSTEINBERG if dither == "fs" else Image.Dither.NONE
        indexed = rgb.quantize(palette=palette_image, dither=mode)

    quantized_palette = indexed.getpalette() or full_palette
    first_index: dict[RGB, int] = {}
    for index in range(256):
        offset = index * 3
        color = tuple(quantized_palette[offset : offset + 3])
        if len(color) == 3:
            first_index.setdefault(color, index)  # type: ignore[arg-type]
    result_palette: dict[RGB, int] = {}
    for index, color in enumerate(colors):
        result_palette.setdefault(color, index)
    return bytearray(result_palette[tuple(quantized_palette[i * 3 : i * 3 + 3])] for i in indexed.tobytes())


def convert_indices(rgb: Image.Image, visible: bytearray, palette_mode: str, color_count: int,
                    dither: str, reserve_transparent: bool) -> tuple[bytearray, list[RGB]]:
    rgb = reduce_to_ocs(rgb, palette_mode)
    samples = [pixel for pixel, is_visible in zip(pixels(rgb), visible) if is_visible]
    palette = make_palette(samples, color_count - int(reserve_transparent))

    image_pixels = pixels(rgb)
    for i, is_visible in enumerate(visible):
        if not is_visible:
            image_pixels[i] = palette[0]
    rgb.putdata(image_pixels)
    indices = quantize_to_palette(rgb, palette, dither)

    output_palette = list(palette)
    if reserve_transparent:
        output_palette.insert(0, (0, 0, 0))
        indices = bytearray(0 if not is_visible else index + 1
                            for index, is_visible in zip(indices, visible))
    else:
        indices = bytearray(0 if not is_visible else index
                            for index, is_visible in zip(indices, visible))
    output_palette.extend([(0, 0, 0)] * (color_count - len(output_palette)))
    return indices, output_palette


def pack_plane(indices: Sequence[int], width: int, height: int, plane: int | None = None) -> bytes:
    data = bytearray()
    for y in range(height):
        for chunk in range(width // 16):
            word = 0
            for bit in range(16):
                value = indices[y * width + chunk * 16 + bit]
                set_bit = value if plane is None else (value >> plane) & 1
                if set_bit:
                    word |= 1 << (15 - bit)
            data.extend(struct.pack(">H", word))
    return bytes(data)


def encode_bob(indices: Sequence[int], visible: bytearray, width: int, height: int, depth: int) -> tuple[bytes, bytes]:
    plane_data = b"".join(pack_plane(indices, width, height, plane) for plane in range(depth))
    mask_data = pack_plane(visible, width, height)
    return plane_data, mask_data


def encode_sprite_channel(indices: Sequence[int], width: int, height: int, bit_shift: int = 0,
                          attach: bool = False) -> bytes:
    chunks = width // 16
    header = [0] * (2 * chunks)
    if attach:
        header[chunks] = 0x0080
    words = list(header)
    for y in range(height):
        for plane in range(2):
            for chunk in range(chunks):
                word = 0
                for bit in range(16):
                    pixel = indices[y * width + chunk * 16 + bit]
                    if ((pixel >> (bit_shift + plane)) & 1) != 0:
                        word |= 1 << (15 - bit)
                words.append(word)
    words.extend([0] * (2 * chunks))
    return struct.pack(f">{len(words)}H", *words)


def palette_value(color: RGB, palette_mode: str) -> int:
    r, g, b = color
    if palette_mode == "ocs":
        return (channel_8to4(r) << 8) | (channel_8to4(g) << 4) | channel_8to4(b)
    return (r << 16) | (g << 8) | b


def pack_palette(palette: Sequence[RGB], palette_mode: str) -> bytes:
    word_format = ">H" if palette_mode == "ocs" else ">I"
    return b"".join(struct.pack(word_format, palette_value(color, palette_mode)) for color in palette)


def format_palette(color: RGB, palette_mode: str) -> str:
    value = palette_value(color, palette_mode)
    return f"${value:04x}" if palette_mode == "ocs" else f"${value:06x}"


def p8_palette_block(block_name: str, variable: str, palette: Sequence[RGB], palette_mode: str,
                     comment: str) -> str:
    datatype = "uword" if palette_mode == "ocs" else "long"
    values = [format_palette(color, palette_mode) for color in palette]
    lines = [f"{block_name} {{", f"    ; {comment}", f"    {datatype}[{len(values)}] {variable} = ["]
    for offset in range(0, len(values), 4):
        lines.append("        " + ", ".join(values[offset : offset + 4]) + ",")
    lines.extend(["    ]", "}"])
    return "\n".join(lines)


def p8_data_block(block_name: str, files: Sequence[tuple[str, str]], alignment: int) -> str:
    lines = [f"{block_name} {{", "    %option amiga_chipram"]
    for label, filename in files:
        lines.extend([f"    %align {alignment}", f"    {label}:", f'    %asmbinary "{filename}"'])
    lines.append("}")
    return "\n".join(lines)


def safe_identifier(name: str) -> str:
    identifier = re.sub(r"\W", "_", name)
    if not identifier or identifier[0].isdigit():
        identifier = "image_" + identifier
    return identifier


def write_output(prefix: Path, data_files: Sequence[tuple[str, bytes]], include: str) -> None:
    prefix.parent.mkdir(parents=True, exist_ok=True)
    for suffix, data in data_files:
        path = Path(str(prefix) + suffix)
        path.write_bytes(data)
        print(f"wrote {path} ({len(data)} bytes)")
    include_path = prefix.parent / f"{safe_identifier(prefix.name)}.p8"
    include_path.write_text(include + "\n")
    print(f"wrote {include_path}")


def add_image_options(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("input", type=Path, help="Pillow-readable source image")
    parser.add_argument("output_prefix", type=Path,
                        help="prefix for output data .bin files, -palette.bin and Prog8 .p8 include")
    parser.add_argument("--palette", choices=("ocs", "aga"), default=None,
                        help="palette format: OCS/ECS 12-bit $0RGB or AGA 24-bit $rrggbb (default: ocs)")
    parser.add_argument("-d", "--dither", choices=DITHER_MODES, default="none",
                        help="quantization dither: none, fs, ordered4, ordered8 (default: none)")
    parser.add_argument("--transparent-color", type=parse_rgb, metavar="RRGGBB",
                        help="treat this exact RGB color as transparent (sprites map it to pen 0)")
    parser.add_argument("--transparent-index", type=int,
                        help="map this P-mode source index to transparent pen 0 (or a clear BOB mask bit)")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Convert images to Amiga BOB or hardware-sprite chip-RAM data. Images are never resized. "
                    "Writes raw data buffers, a raw palette file, and a Prog8 include with the palette array."
    )
    subparsers = parser.add_subparsers(dest="kind", required=True)

    bob = subparsers.add_parser("bob", help="convert to BOB bitplanes and mask")
    add_image_options(bob)
    bob.add_argument("--depth", type=int, choices=range(1, 9), required=True,
                     help="color bitplanes (1-6 for OCS, 1-8 for AGA)")

    sprite = subparsers.add_parser("sprite", help="convert to hardware sprite data")
    add_image_options(sprite)
    sprite.add_argument("--mode", choices=("ecs", "aga"), required=True,
                        help="ECS uses fixed 16-pixel sprites; AGA supports 16/32/64-pixel sprites")
    sprite.add_argument("--colors", type=int, choices=(4, 16), default=16,
                        help="total palette entries including transparent pen 0: 4 = one channel, 16 = attached pair (default: 16)")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    try:
        if not args.input.is_file():
            raise ValueError(f"input image does not exist: {args.input}")
        if args.transparent_index is not None and not 0 <= args.transparent_index <= 255:
            raise ValueError("--transparent-index must be in range 0..255")
        rgb, visible, width, height = load_source(args.input, args.transparent_color, args.transparent_index)
        palette_mode = args.palette or ("aga" if args.kind == "sprite" and args.mode == "aga" else "ocs")

        if args.kind == "bob":
            max_depth = 6 if palette_mode == "ocs" else 8
            if args.depth > max_depth:
                raise ValueError(f"{palette_mode.upper()} BOB depth must be at most {max_depth}")
            if width % 16 != 0:
                raise ValueError(f"BOB width must be a multiple of 16 pixels; got {width}")
            if width > 1024:
                raise ValueError("draw_bob supports at most 64 words (1024 pixels) per row")
            indices, palette = convert_indices(rgb, visible, palette_mode, 1 << args.depth,
                                               args.dither, reserve_transparent=False)
            planes, mask = encode_bob(indices, visible, width, height, args.depth)
            stem = safe_identifier(args.output_prefix.name)
            data_block = p8_data_block(f"{stem}_bob_data", [
                ("planes", args.output_prefix.name + ".planes.bin"),
                ("mask", args.output_prefix.name + ".mask.bin"),
            ], 4)
            palette_block = p8_palette_block(f"{stem}_bob_palette", "colors", palette, palette_mode,
                                             f"BOB palette; alternate raw palette is {args.output_prefix.name}-palette.bin.")
            include = data_block + "\n\n" + palette_block
            write_output(args.output_prefix, [
                (".planes.bin", planes),
                (".mask.bin", mask),
                ("-palette.bin", pack_palette(palette, palette_mode)),
            ], include)
            print(f"BOB: {width}x{height}, {args.depth} planes, {palette_mode.upper()} palette")
            return

        if palette_mode == "aga" and args.mode != "aga":
            raise ValueError("an AGA palette requires --mode aga for sprites")
        if args.mode == "ecs" and width != 16:
            raise ValueError(f"ECS sprite width must be exactly 16 pixels; got {width}")
        if args.mode == "aga" and width not in (16, 32, 64):
            raise ValueError(f"AGA sprite width must be 16, 32, or 64 pixels; got {width}")
        color_count = args.colors
        indices, palette = convert_indices(rgb, visible, palette_mode, color_count,
                                           args.dither, reserve_transparent=True)
        if args.mode == "aga":
            palette.extend([(0, 0, 0)] * (16 - len(palette)))
        if color_count == 16:
            even = encode_sprite_channel(indices, width, height, bit_shift=0)
            odd = encode_sprite_channel(indices, width, height, bit_shift=2, attach=True)
            files = [(".even.bin", even), (".odd.bin", odd)]
            data_files = [("even", args.output_prefix.name + ".even.bin"),
                          ("odd", args.output_prefix.name + ".odd.bin")]
            mode_comment = "Attached 16-entry sprite palette; pen 0 is transparent; even supplies low 2 bits, odd supplies high 2 bits."
        else:
            image = encode_sprite_channel(indices, width, height)
            files = [(".sprite.bin", image)]
            data_files = [("sprite", args.output_prefix.name + ".sprite.bin")]
            mode_comment = "Single 4-color sprite; palette index 0 is transparent."
        alignment = {16: 2, 32: 4, 64: 16}[width] if args.mode == "aga" else 2
        data_block = p8_data_block(f"{safe_identifier(args.output_prefix.name)}_sprite_data", data_files, alignment)
        if args.mode == "aga":
            loader = ("sprites.aga_set_colors16(&colors as ^^long), "
                      "then sprites.aga_set_bank(1, 1)") if palette_mode == "aga" else (
                      "sprites.set_colors16(&colors as ^^uword), then select bank 1")
        elif color_count == 16:
            loader = "sprites.set_colors16(&colors as ^^uword)"
        else:
            loader = "sprites.set_colors(pair, colors[1], colors[2], colors[3])"
        palette_comment = (f"{mode_comment} Load with {loader}. "
                           f"Alternate raw palette: {args.output_prefix.name}-palette.bin.")
        palette_block = p8_palette_block(f"{safe_identifier(args.output_prefix.name)}_sprite_palette",
                                         "colors", palette, palette_mode, palette_comment)
        files.append(("-palette.bin", pack_palette(palette, palette_mode)))
        write_output(args.output_prefix, files, data_block + "\n\n" + palette_block)
        print(f"Sprite: {width}x{height}, {args.mode.upper()}, {color_count} colors, {palette_mode.upper()} palette")
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
