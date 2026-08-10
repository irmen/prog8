#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
output="${1:-${root_dir}/amigaiff.adf}"
library="${IFFPARSE_LIBRARY:-${root_dir}/Libs/iffparse.library}"

if ! command -v xdftool >/dev/null 2>&1; then
    printf '%s\n' "error: xdftool from amitools is not installed or not on PATH" >&2
    exit 1
fi

if [[ ! -f "$library" ]]; then
    printf '%s\n' "error: iffparse.library not found: $library" >&2
    exit 1
fi

xdftool -f "$output" create + \
    format PROG8 ffs + \
    boot install + \
    makedir Libs + \
    write "$library" Libs/iffparse.library

printf 'Created Amiga floppy image: %s\n' "$output"
xdftool "$output" list / all info
