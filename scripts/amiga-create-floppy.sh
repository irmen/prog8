#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-${root_dir}/amigaiff.adf}"
program="${AMIGA_PROGRAM:-${root_dir}/amigaiff}"
library="${IFFPARSE_LIBRARY:-${root_dir}/Libs/iffparse.library}"
image="${PSYGNOSIS_IMAGE:-${root_dir}/psygnosis.iff}"
cara_image="${CARA_IMAGE:-${root_dir}/cara.iff}"
izx0_image="${IZX0_IMAGE:-${root_dir}/cara.izx0}"
izx0_reader="${IZX0_READER:-${root_dir}/izx0reader}"

if ! command -v xdftool >/dev/null 2>&1; then
    printf '%s\n' "error: xdftool from amitools is not installed or not on PATH" >&2
    exit 1
fi

if [[ ! -f "$program" ]]; then
    printf '%s\n' "error: Amiga program not found: $program" >&2
    exit 1
fi

if [[ ! -f "$library" ]]; then
    printf '%s\n' "error: iffparse.library not found: $library" >&2
    exit 1
fi

if [[ ! -f "$image" ]]; then
    printf '%s\n' "error: IFF image not found: $image" >&2
    exit 1
fi

if [[ ! -f "$cara_image" ]]; then
    printf '%s\n' "error: cara IFF image not found: $cara_image" >&2
    exit 1
fi

if [[ ! -f "$izx0_image" ]]; then
    printf '%s\n' "error: IZX0 image not found: $izx0_image" >&2
    exit 1
fi

if [[ ! -f "$izx0_reader" ]]; then
    printf '%s\n' "error: IZX0 reader not found: $izx0_reader" >&2
    exit 1
fi

xdftool -f "$output" create + \
    format PROG8 ofs + \
    boot install + \
    makedir Libs + \
    write "$library" Libs/iffparse.library + \
    write "$program" amigaiff + \
    write "$image" psygnosis.iff + \
    write "$cara_image" cara.iff + \
    write "$izx0_image" cara.izx0 + \
    write "$izx0_reader" izx0reader

printf 'Created FFS Amiga floppy image: %s\n' "$output"
xdftool "$output" list / all info
