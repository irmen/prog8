# Linking the kernel

The kernel is linked using **vlink**, producing an ELF32 binary (`kernel.elf`) that qemu loads directly via its `-kernel` option.

## Toolchain

| Tool | Role |
|---|---|
| `vasmm68k_mot` | Assembles `kernel.S` → `kernel.o` |
| `vlink` | Links `kernel.o` → `kernel.elf` using `link.ld` |

The `Makefile` invocation:

```makefile
vlink -b elf32m68k -n -T link.ld -o kernel.elf kernel.o
```

## Command-line flags

### `-b elf32m68k` — output format

Tells vlink to produce a 32-bit big-endian m68k ELF binary.

vlink supports many output formats; without this flag it would default to something else. Note that vlink ignores the `OUTPUT_FORMAT()` directive inside linker scripts, so the format **must** be specified on the command line.

### `-n` — NMAGIC / no page alignment

Disables page-boundary alignment of ELF segments.

Without `-n`, vlink aligns the LOAD segment to a full page (0x2000 = 8 KB). The resulting ELF has a large gap of padding between the ELF header and the actual code:

```
Type   Offset   VirtAddr   PhysAddr   Align
LOAD   0x002000 0x00010000 0x00010000 0x2000
```

qemu's ELF loader gets confused by this layout and either fails to load the kernel or jumps to the wrong address.

With `-n`, segment alignment is reduced to the minimum (0x10) and the code starts right after the ELF header:

```
Type   Offset   VirtAddr   PhysAddr   Align
LOAD   0x000080 0x00010000 0x00010000 0x10
```

qemu loads this cleanly — the code is at file offset 0x80, maps to virtual address 0x10000, and execution starts at `_start` as expected.

`-n` is the vlink equivalent of GNU ld's `-N` / `--omagic` option.

### `-T link.ld` — linker script

Passes the linker script (see below).

## The linker script (`link.ld`)

```
ENTRY(_start)
SECTIONS
{
  . = 0x10000;
  .text : { *(.text) }
  .data : { *(.data) }
  .bss  : { *(.bss)  *(COMMON) }
  _end_of_code = .;
}
```

### Line by line

| Line | What it does |
|---|---|
| `ENTRY(_start)` | Sets the ELF entry point to `_start` — the first instruction in `kernel.S`. qemu uses this to know where to jump after loading the kernel. |
| `SECTIONS { … }` | Opens the section layout block — the core of any linker script. Everything inside describes how to arrange the output binary. |
| `. = 0x10000;` | Sets the **load address** to `0x10000` (64 KB). This is where qemu places the kernel in memory and begins execution. The `.` is the *location counter* — it advances automatically as sections are placed. |
| `.text : { *(.text) }` | Collects all `.text` (code) sections from every input object file into one output `.text` section. |
| `.data : { *(.data) }` | Same for initialised data. |
| `.bss  : { *(.bss) *(COMMON) }` | Same for uninitialised (zero-filled) data. `COMMON` catches any unresolved global variables that the assembler emits as common symbols. |
| `_end_of_code = .;` | Defines the symbol `_end_of_code` at the address immediately after the last section. `kernel.S` references this symbol to find where the bootinfo records begin in memory (qemu appends them just past the kernel image). |

### Why a linker script is required

vlink has no command-line flag equivalent to `. = 0x10000` (setting the load address). Attempting `-Ttext=0x10000` causes a fatal error because vlink treats the `=` as part of a filename. The correct syntax `-Ttext 0x10000` (space-separated) does set the address, but then `_end_of_code` is undefined because there is no linker script to define it.

The linker script is therefore the minimum required: it sets the load address and defines `_end_of_code`.
