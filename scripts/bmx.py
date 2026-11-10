#!/usr/bin/env python
# package requirements: pillow

import argparse
import struct
from PIL import Image
from typing import List

if __package__:
    from .cx16images import (
        BitmapImage,
        BAYER_4X4,
        BAYER_8X8,
        apply_ordered_dither,
    )
else:
    from cx16images import (
        BitmapImage,
        BAYER_4X4,
        BAYER_8X8,
        apply_ordered_dither,
    )


class Bmx:
    header_fmt = "<3sBBBHHBBHbB"
    VERSION = 1

    class BmxError(RuntimeError):
        pass

    def __init__(self):
        assert struct.calcsize(Bmx.header_fmt) == 16
        self.magic = b"BMX"
        self.version = Bmx.VERSION
        self.bpp = 0
        self.vera_color_depth = 0
        self.width = 0
        self.height = 0
        self.pal_used = 0
        self.pal_start = 0
        self.data_start = 0
        self.compressed = 0
        self.border = 0
        self.palette_data = b""
        self.bitmap_data = b""

    def __str__(self) -> str:
        cmpr = "compressed" if self.compressed else "uncompressed"
        return f"[BMX image {self.width}*{self.height}, {self.bpp} bpp, {self.pal_used} colors, {cmpr}]"

    def read(self, filename: str):
        data = open(filename, "rb").read()
        self.__parse_header(data)
        self.palette_data = data[32: 32 + self.pal_used * 2]
        self.bitmap_data = data[32 + self.pal_used * 2:]
        if self.bpp not in (1, 2, 4, 8):
            raise self.BmxError("invalid bpp")
        bytes_per_row = (self.width * self.bpp + 7) // 8
        expected_bitmap_data_size = bytes_per_row * self.height
        if len(self.bitmap_data) > expected_bitmap_data_size:
            print(f"warning: invalid bitmap data size, got {len(self.bitmap_data)} expected {expected_bitmap_data_size}. Truncated.")
            self.bitmap_data = self.bitmap_data[:expected_bitmap_data_size]
        elif len(self.bitmap_data) < expected_bitmap_data_size:
            print(f"warning: invalid bitmap data size, got {len(self.bitmap_data)} expected {expected_bitmap_data_size}.")

    def write(self, filename: str) -> None:
        header_pal_used = self.pal_used if self.pal_used < 256 else 0
        header = struct.pack(Bmx.header_fmt, self.magic, self.version, self.bpp, self.vera_color_depth, self.width,
                                self.height, header_pal_used, self.pal_start, self.data_start, self.compressed,
                                self.border) + bytes(16)
        with open(filename, "wb") as out:
            out.write(header)
            out.write(self.palette_data)
            out.write(self.bitmap_data)

    def as_image(self) -> Image:
        """Convert to a 8bpp pillow image that can easily be saved, shown, etc"""
        self.__decompress_if_compressed()
        if self.bpp == 8:
            img = Image.frombytes("P", (self.width, self.height), self.bitmap_data)
        elif self.bpp in (1, 2, 4):
            bytes_per_row = (self.width * self.bpp + 7) // 8
            pixel_mask = (1 << self.bpp) - 1
            pixels = bytearray(self.width * self.height)
            for y in range(self.height):
                row_offset = y * bytes_per_row
                for x in range(self.width):
                    bit_offset = x * self.bpp
                    byte = self.bitmap_data[row_offset + bit_offset // 8]
                    shift = 8 - self.bpp - bit_offset % 8
                    pixels[y * self.width + x] = (byte >> shift) & pixel_mask
            img = Image.frombytes("P", (self.width, self.height), bytes(pixels))
        else:
            raise self.BmxError(f"no support for converting bpp {self.bpp} to an image")
        palette = []
        for rgb in self.__get_palette_rgb32():
            palette.append(rgb[0])
            palette.append(rgb[1])
            palette.append(rgb[2])
        img.putpalette(palette)
        return img

    def from_image(self, image: Image, max_width: int = 320, max_height: int = 240, preserve_first_16_colors=False, dither: str = "none", bpp: int = 8) -> None:
        """Create a BMX image from the given Pillow image."""
        if bpp not in (1, 2, 4, 8):
            raise ValueError("bpp must be 1, 2, 4, or 8")
        if preserve_first_16_colors and bpp not in (4, 8):
            raise ValueError("preserving the default 16 colors requires 4 or 8 bpp")
        if dither not in ("none", "fs", "ordered4", "ordered8"):
            raise ValueError("dither must be none, fs, ordered4, or ordered8")
        palette_limit = 1 << bpp
        bitmap_image = BitmapImage("", image)
        if bitmap_image.width > max_width or bitmap_image.height > max_height:
            print(f"Scaling image to fit within {max_width}x{max_height}, preserving aspect ratio")
        bitmap_image.constrain_size(max_width=max_width, max_height=max_height)
        if bitmap_image.width > 640 or bitmap_image.height > 480:
            raise self.BmxError("image exceeds the X16's 640x480 resolution limit")
        pixels_per_byte = 8 // bpp
        aligned_width = bitmap_image.width - bitmap_image.width % pixels_per_byte
        if aligned_width == 0:
            raise self.BmxError(f"image width {bitmap_image.width} is too small for {bpp}bpp byte alignment")
        if aligned_width != bitmap_image.width:
            print(f"Warning: cropping {bitmap_image.width - aligned_width} right-edge pixels for {bpp}bpp byte alignment")
            bitmap_image = bitmap_image.crop(0, 0, aligned_width, bitmap_image.height)

        preserve_palette = bitmap_image.has_palette() and bpp == 8 and not preserve_first_16_colors and dither == "none"
        if not preserve_palette:
            if dither in ("ordered4", "ordered8"):
                source_image = bitmap_image.get_image().convert("RGB")
                palette_image = BitmapImage("", source_image.copy())
                palette_image.quantize(bpp, preserve_first_16_colors=preserve_first_16_colors, dither=Image.Dither.NONE)
                matrix = BAYER_4X4 if dither == "ordered4" else BAYER_8X8
                ordered = apply_ordered_dither(source_image, palette_image.get_image(), matrix)
                bitmap_image = BitmapImage("", ordered)
            else:
                dither_mode = Image.Dither.FLOYDSTEINBERG if dither == "fs" else Image.Dither.NONE
                bitmap_image.quantize(bpp, preserve_first_16_colors=preserve_first_16_colors, dither=dither_mode)

        self.width, self.height = bitmap_image.size
        self.set_bpp(bpp)
        palette = bitmap_image.get_palette()
        if not palette or len(palette) > 1 << bpp:
            raise self.BmxError("invalid palette size for bitmap depth")
        self.pal_used = len(palette)
        x16_palette = []
        for r, g, b in palette:
            x16_palette.append(self.__to4bit(g) << 4 | self.__to4bit(b))
            x16_palette.append(self.__to4bit(r))
        self.data_start = 32 + 2 * self.pal_used
        self.palette_data = bytes(x16_palette)
        pixels = bitmap_image.get_all_pixels_8bpp()
        if bpp == 8:
            self.bitmap_data = bytes(pixels)
        else:
            bytes_per_row = (self.width * bpp + 7) // 8
            packed = bytearray(bytes_per_row * self.height)
            pixel_mask = palette_limit - 1
            for y in range(self.height):
                row_offset = y * bytes_per_row
                for x in range(self.width):
                    bit_offset = x * bpp
                    byte_offset = row_offset + bit_offset // 8
                    shift = 8 - bpp - bit_offset % 8
                    packed[byte_offset] |= (pixels[y * self.width + x] & pixel_mask) << shift
            self.bitmap_data = bytes(packed)
        vram_required = len(self.bitmap_data) + len(self.palette_data)
        if vram_required > 128 * 1024:
            raise self.BmxError(f"image and palette need {vram_required} bytes, exceeding the X16's 128 KiB of VERA VRAM")

    def set_bpp(self, bpp: int) -> None:
        self.bpp = bpp
        if bpp == 1:
            self.vera_color_depth = 0
        elif bpp == 2:
            self.vera_color_depth = 1
        elif bpp == 4:
            self.vera_color_depth = 2
        elif bpp == 8:
            self.vera_color_depth = 3
        else:
            raise ValueError("bpp must be 1,2,4,8")

    def set_vera_colordepth(self, depth: int) -> None:
        self.vera_color_depth = depth
        self.bpp = 2 ** depth
        if depth < 0 or depth > 3:
            raise ValueError("depth must be 0,1,2,3")

    def __parse_header(self, bmxdata: bytes) -> None:
        if bmxdata[0:3] != b"BMX":
            raise self.BmxError("not a BMX file")
        self.magic, self.version, self.bpp, self.vera_color_depth, self.width, self.height, self.pal_used, self.pal_start, self.data_start, self.compressed, self.border = struct.unpack(
            Bmx.header_fmt, bmxdata[:16])
        if self.magic != b"BMX":
            raise self.BmxError("not a BMX file")
        if self.version != Bmx.VERSION:
            raise self.BmxError("invalid BMX version, only supports " + str(Bmx.VERSION))
        if self.pal_used == 0:
            self.pal_used = 256
        if self.data_start != 32 + 2 * self.pal_used:
            print("Warning: data start offset mismatch:", self.data_start, "expected:", 32 + 2 * self.pal_used)

    def __get_palette_rgb32(self) -> List[tuple]:
        rgb = []
        for i in range(0, self.pal_used * 2, 2):
            G4 = self.palette_data[i] >> 4
            B4 = self.palette_data[i] & 15
            R4 = self.palette_data[i + 1] & 15
            rgb.append((R4 << 4 | R4, G4 << 4 | G4, B4 << 4 | B4))
        return rgb

    def __to4bit(self, color: int) -> int:
        # more accurate colorspace reduction, see https://threadlocalmutex.com/?p=48
        return (color * 15 + 135) >> 8

    def __decompress_if_compressed(self) -> None:
        if self.compressed:
            raise self.BmxError("doesn't support compressed images (spec not finalized and not implemented here)")
        # TODO decompress compressed BMX (spec not finalized for this yet)...
        self.compressed = False


def bmx_show(filename: str) -> None:
    bmx = Bmx()
    bmx.read(filename)
    print(f"showing: {bmx}")
    img = bmx.as_image()
    img.show()


def bmx_info(filename: str) -> None:
    bmx = Bmx()
    bmx.read(filename)
    print(f"{filename}: {bmx}")


def bmx_create(filename: str, outputfile: str, preserve16: bool, dither: str, bpp: int, hires: bool = False) -> None:
    img = Image.open(filename)
    max_width, max_height = (640, 480) if hires else (320, 240)
    bmx = Bmx()
    bmx.from_image(img, max_width=max_width, max_height=max_height, preserve_first_16_colors=preserve16, dither=dither, bpp=bpp)
    bmx.write(outputfile)
    print(f"wrote {filename} as {outputfile} {bmx}")


def bmx_convert(filename: str, outputfile: str) -> None:
    bmx = Bmx()
    bmx.read(filename)
    img = bmx.as_image()
    img.save(outputfile)
    print(f"wrote {filename} {bmx} as {outputfile}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Commander-X16 'BMX' image format converter")
    parser.add_argument("--preserve16", action="store_true", default=False, help="preserve first 16 default Vera color palette entries (in 8bpp/256 colors mode)")
    parser.add_argument("--bpp", default=8, type=int, help="bits per pixel of the output bmx image (default: 8)", choices=[1, 2, 4, 8])
    parser.add_argument("--hires", action="store_true", help="scale to fit within 640x480 instead of 320x240")
    parser.add_argument("-d", "--dither", nargs="?", const="fs", default="none", choices=("none", "fs", "ordered4", "ordered8"), metavar="MODE", help="dithering when quantizing: none, fs (Floyd-Steinberg), ordered4 (4x4 Bayer), ordered8 (8x8 Bayer). Bare --dither means fs. (default: none)")
    parser.add_argument("action", help="the action to perform", type=str, choices=["show", "info", "tobmx", "topng"])
    parser.add_argument("filename", help="the file to process", type=str)
    parser.add_argument("output", nargs="?", help="output path (required for tobmx and topng)")
    args = parser.parse_args()
    if args.action == "show":
        if args.output is not None:
            parser.error("show does not take an output path")
        bmx_show(args.filename)
    elif args.action == "info":
        if args.output is not None:
            parser.error("info does not take an output path")
        bmx_info(args.filename)
    elif args.action == "tobmx":
        if args.output is None:
            parser.error("tobmx requires an output path")
        if args.preserve16 and args.bpp not in (4, 8):
            parser.error("--preserve16 requires 4 or 8 bpp output")
        try:
            bmx_create(args.filename, args.output, args.preserve16, args.dither, args.bpp, args.hires)
        except Bmx.BmxError as error:
            parser.error(str(error))
    elif args.action == "topng":
        if args.output is None:
            parser.error("topng requires an output path")
        bmx_convert(args.filename, args.output)
