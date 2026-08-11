#!/usr/bin/env python3
"""ZX0-compress a file with salvador and prepend a big-endian 32-bit
header containing the original uncompressed file size."""

import argparse
import os
import struct
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(
        prog="zx0-compress-prefix-size",
        description="Compress sourcefile with salvador into destinationfile, "
                    "prepending a big-endian 32-bit original size header.",
    )
    parser.add_argument("sourcefile", type=str, help="Path to source file")
    parser.add_argument("destinationfile", type=str, help="Path for target destination file")
    args = parser.parse_args()

    if not os.path.exists(args.sourcefile):
        parser.error(f"Source file '{args.sourcefile}' does not exist.")

    size = os.path.getsize(args.sourcefile)

    subprocess.run(
        ["salvador", "-c", args.sourcefile, args.destinationfile],
        check=True,
    )

    header = struct.pack(">I", size)
    tmpfile = args.destinationfile + ".tmp"
    with open(args.destinationfile, "rb") as f:
        compressed = f.read()
    with open(tmpfile, "wb") as f:
        f.write(header)
        f.write(compressed)
    os.replace(tmpfile, args.destinationfile)


if __name__ == "__main__":
    main()
