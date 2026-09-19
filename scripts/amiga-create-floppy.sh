#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $# -lt 1 ]]; then
    printf 'usage: %s <content-dir> [output.adf]\n' "$(basename "${BASH_SOURCE[0]}")" >&2
    printf '  packs <content-dir> into a bootable Amiga floppy image\n' >&2
    exit 2
fi

content_dir="$1"
if [[ ! -d "$content_dir" ]]; then
    printf 'error: not a directory: %s\n' "$content_dir" >&2
    exit 1
fi

output="${2:-${root_dir}/$(basename -- "$content_dir").adf}"
volume="${FLOPPY_VOLUME:-PROG8}"

if ! command -v xdftool >/dev/null 2>&1; then
    printf '%s\n' "error: xdftool from amitools is not installed or not on PATH" >&2
    exit 1
fi

# format creates a fresh FFS volume named <volume>, 'boot install' adds the standard
# AmigaDOS boot block so the floppy runs its startup-sequence on boot, and 'pack' (last)
# recursively drops the contents of <content_dir> (files and subdirs) into the volume root.
xdftool -f "$output" format "$volume" ffs + boot install + pack "$content_dir"

printf 'Created Amiga floppy image: %s (volume %s)\n' "$output" "$volume"
xdftool "$output" list / all info
