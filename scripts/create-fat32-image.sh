#!/bin/bash
# Create a 2GB FAT32 formatted SD card image using mtools

if [ -z "$1" ]; then
    echo "Usage: $0 <output.img>"
    exit 1
fi

OUTFILE="$1"
TMPCONF=$(mktemp)

# Create 2GB image file
truncate -s 2G "$OUTFILE"

# Initialize partition table and create FAT32 partition (type 0x0c)
mpartition -i "$OUTFILE|partition=1" -I ::
mpartition -i "$OUTFILE|partition=1" -I -c -b 2048 -T 0x0C ::
mformat -i "$OUTFILE|partition=1" -F -v SDCARD ::

echo "Created $OUTFILE - 2GB FAT32 SD card image"
mdir -i "$OUTFILE@@1M"

