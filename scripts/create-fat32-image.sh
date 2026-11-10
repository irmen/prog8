#!/bin/bash
# Create a FAT32 formatted SD card image using mtools
# Usage: create-fat32-image.sh <output.img> [size] [-f]
#   size: image size, e.g. 2G (default), 512M
#   -f: overwrite the output file if it already exists

set -euo pipefail

if [ $# -lt 1 ]; then
    echo "Usage: $0 <output.img> [size] [-f]" >&2
    exit 1
fi

OUTFILE="$1"
SIZE="${2:-2G}"
FORCE=0
if [ "${3:-}" = "-f" ]; then
    FORCE=1
fi

if [ -e "$OUTFILE" ] && [ "$FORCE" -ne 1 ]; then
    echo "error: $OUTFILE already exists (pass -f to overwrite)" >&2
    exit 1
fi

for tool in truncate mpartition mformat mdir; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "error: required tool '$tool' not found (mtools)" >&2
        exit 1
    fi
done

# Create image file
truncate -s "$SIZE" "$OUTFILE"

# Initialize partition table and create FAT32 partition (type 0x0c), starting at 1M
PARTSPEC="$OUTFILE|partition=1"
mpartition -i "$PARTSPEC" -I ::
mpartition -i "$PARTSPEC" -I -c -b 2048 -T 0x0C ::
mformat -i "$PARTSPEC" -F -v SDCARD ::

echo "Created $OUTFILE - $SIZE FAT32 SD card image"
mdir -i "$PARTSPEC"
