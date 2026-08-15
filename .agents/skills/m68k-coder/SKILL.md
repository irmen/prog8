---
name: m68k-coder
description: Writing or understanding Motorola 68000/68020 assembly in vasm mot syntax, standalone or embedded in Prog8
---

# M68K Assembly Coder

Use Motorola syntax for `vasm`, not Devpac or other assemblers. Keep answers
concise and practical.

## Syntax

- Labels begin in column 1. Instructions are indented at least four spaces.
- Use lowercase opcodes and operands, two spaces between opcode and operand,
  and two spaces before end-of-line comments.
- Use `.b`, `.w`, and `.l` for byte, word, and long operations. Do not rely on
  default sizes.
- Hex is `$1234`, binary is `%1010`, decimal is `123`, and characters use
  `#'A'`.
- Local labels start with `.`. Equates use `name = value`.
- Data directives are `dc.b`, `dc.w`, `dc.l`; allocation directives are
  `ds.b`, `ds.w`, `ds.l`.
- Sections use forms such as `SECTION .text,code` and `SECTION .bss,bss`.

```asm
        moveq   #0,d0
        move.w  value,d0
        add.l   d1,d0
.loop
        dbra    d0,.loop
```

## Architecture

- Registers `D0-D7` and `A0-A7` are 32-bit; `A7` is the stack pointer.
- Pointers are 32-bit and must normally be accessed with `.l`.
- Memory is big-endian: `$1234` is stored as `$12,$34` and a long's low byte
  is at offset `+3`.
- Word and long accesses must be even-aligned on 68000. 68020 permits
  misalignment but it is slower.
- The 68000 (`amiga500`) has only word multiply/divide and no scaled indexing,
  bit-field, or 32-bit multiply/divide instructions. The 68020 (`qemu68k`)
  adds those features and `extb.l`.
- On 68000, sign-extend a byte with `ext.w` followed by `ext.l`.
- `move.b` and `move.w` to a data register do not clear its upper bits. Like
  `move.w` only writes the lower 16 bits, `move.b` only writes the lower 8 bits;
  both leave all other bits of the register untouched. So zero-extending a byte
  to long requires `moveq #0,d0` first, then `move.b src,d0`, then `move.l d0,dst`
  (or mask with `and.l #$ff` / `and.l #$ffff`). Relying on `move.b` to clear the
  upper bits is a common bug that corrupts values.
- `movea` does not set condition codes; `move` does. Keep the stack word
  aligned and push words or longs, never bytes.

## Registers and Calls

- Return values use `D0` (primary), `D1` and `D2` (additional), or `A0` for a
  pointer.
- `D0-D1` and `A0-A1` are scratch registers. Preserve registers required by
  the surrounding calling convention, especially `D2-D7` and `A2-A6`.
- `move.l d0,-(sp)` pushes and `move.l (sp)+,d0` pops.

## Prog8 Integration

Assembly can appear in `%asm {{ }}` blocks or `asmsub` routines. `asmsub`
parameter names are documentation only: use the actual annotated registers.
Declare every modified register in `clobbers`.

```prog8
asmsub add100(uword value @D0) -> uword @D0 clobbers (D0) {
    %asm {{
        add.w  #100,d0
    }}
}
```

Supported annotations include `@D0`-`@D7`, `@A0`-`@A6`, virtual `@R0`-`@R15`,
floating-point `@FP0`-`@FP7`, and flags `@Pc` and `@Pz`.

Prog8 assembly symbols are prefixed:

| Prefix | Symbol |
| --- | --- |
| `p8v_` | variable or parameter |
| `p8s_` | subroutine |
| `p8b_` | block |
| `p8c_` | constant or enum member |
| `p8l_` | label |
| `p8t_` | struct type |

Use fully qualified names when needed, such as
`p8b_main.p8v_value`. `%option no_symbol_prefixing` disables prefixes.

```asm
        move.l  p8v_pointer,a0
        move.w  p8v_word,d0
        jsr     p8s_main.p8s_helper
```

## Useful Patterns

```asm
        lea     source,a0
        lea     dest,a1
.copy
        move.b  (a0)+,(a1)+
        bne.s   .copy
```

Use `moveq` for small constants, `addq`/`subq` for constants 1 through 8, and
`lea` for address arithmetic. Check divisors for zero and division overflow.

## Tools

For QEMU 68k, Prog8 invokes `vasmm68k_mot` with `-m68020 -m68881 -Felf
-opt-speed -warnunaligned -ldots -spaces`, then links with `vlink`. For raw
binary output use `-Fbin` and `-join=0x<addr>`. Add `-L listing.txt` when a
listing is needed.

Prog8 runs `amiga500` programs with `vamos --cpu 68000` and `qemu68k` programs
with `qemu-system-m68k -M virt -cpu m68020 -m 1M -kernel program.elf -nographic`.
