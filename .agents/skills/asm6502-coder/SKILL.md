---
name: asm6502-coder
description: Writing or understanding 6502/65C02 assembly in 64tass syntax, standalone or embedded in Prog8
---

# 6502 Assembly Coder

Use 64tass syntax, not ca65 or another assembler. Keep answers concise and
practical. This applies to standalone `.asm` files and Prog8 `%asm {{ }}` or
`asmsub` blocks.

## Syntax

- Labels begin in column 1. Instructions are indented at least four spaces.
- Use lowercase opcodes and operands, two spaces between opcode and operand,
  and two spaces before end-of-line comments.
- Hex is `$1234`, binary is `%1010`, decimal is `123`.
- Use `.byte`, `.word`, `.dword`, `.fill`, `.text`, and equates such as
  `name = value`.
- Sections use `.section name` and `.send name`.
- Procedures use `.proc` and `.pend`; labels beginning with `_` are local to a
  procedure.
- Conditional assembly uses `.if`, `.elsif`, `.else`, and `.endif`.

## Anonymous Labels

Anonymous label definitions are always a single `+` or `-` in column 1.
References use repeated signs:

- `+` is the next upcoming `+` definition, `++` the second, `+++` the third.
- `-` is the most recent `-` definition, `--` the preceding one, and so on.
- Passing a `+` definition resets the forward-reference count.

```asm
-   dex
    bne -       ; most recent '-' above
    ldx #5
+   dex         ; first upcoming '+'
    bne +       ; this '+'
    sta $400
+   lda #0      ; second '+' definition
    bne ++      ; next '+' after this one
    rts
+   inc $d020   ; third '+' definition
```

## CPU Differences

- `cx16` is the only Prog8 target that supports 65C02 instructions such as
  `stz`, `phx`, `plx`, `phy`, `ply`, `bra`, `trb`, `tsb`, `wai`, `stp`, and
  `bit #imm`. C64, C128, and PET32 use the original 6502.
- Rockwell instructions (`rmb`, `smb`, `bbr`, `bbs`) are not available.
- Use an explicit `a` for accumulator forms such as `rol a`, `lsr a`, `inc a`,
  and `dec a`.
- Branches are relative. Prog8's assembler invocation enables `--long-branch`
  for out-of-range branches.
- `lda`, `ldx`, and `ldy` do not affect carry. `cmp` sets carry for
  `register >= operand`.
- On NMOS 6502, `jmp ($xxff)` wraps the high-byte fetch within the page.
- `BRK` skips the byte after its opcode. The byte is commonly a handler
  signature.
- `bit` memory forms set N and V from the operand and Z from `A AND operand`.
  65C02 `bit #imm` sets only Z.
- Decimal mode can persist into NMOS IRQ handlers. Use `cld` at handler entry
  and explicitly control D before `adc` or `sbc`.

## Prog8 Integration

`asmsub` parameter names are documentation only. Use the actual registers or
define assembly aliases, and list every modified register in `clobbers`.

```prog8
asmsub increment(uword value @R0) clobbers (A, X) {
    %asm {{
        lda  cx16.r0L
        clc
        adc  #1
        sta  cx16.r0L
    }}
}
```

Annotations include `@A`, `@X`, `@Y`, `@AX`, `@AY`, `@R0`-`@R15`, `@FAC1`,
`@FAC2`, `@Pc` (carry), and `@Pz` (zero). Unmapped parameters are accessed as
`p8v_name`.

Prog8 symbols use these prefixes: `p8v_` variables and parameters, `p8s_`
subroutines, `p8b_` blocks, `p8c_` constants, `p8l_` labels, and `p8t_` struct
types. Fully qualified names may look like
`p8b_main.p8s_helper.p8v_local`. `%option no_symbol_prefixing` disables the
prefixes. Split word arrays use `_lsb` and `_msb` symbols.

## Zeropage and Memory

- Never hardcode zeropage addresses. Use `P8ZP_SCRATCH_B1`,
  `P8ZP_SCRATCH_REG`, `P8ZP_SCRATCH_W1`, `P8ZP_SCRATCH_W2`, and
  `P8ZP_SCRATCH_PTR`, or the appropriate `cx16.r0`-`cx16.r15` symbols.
- Scratch variables are not necessarily consecutive. CX16 virtual registers
  are consecutive and have `L` and `H` byte names.
- Allocate additional temporary storage in BSS.
- The hardware stack occupies `$0100-$01ff` and is limited. Do not overflow
  it.
- Prog8 words are little-endian: load the low byte first, then the high byte.

```asm
        clc
        lda  word1
        adc  word2
        sta  result
        lda  word1+1
        adc  word2+1
        sta  result+1
```

## Common Patterns

```asm
        ldy  #index
        lda  (ptr),y

        jsr  p8s_main.p8s_helper
```

Avoid self-modifying code unless it is explicitly required. It cannot run
from ROM and complicates debugging and tooling. Prefer lookup tables, RAM
vectors, or alternative algorithms.

## Tools

Prog8 invokes 64tass with `--ascii --case-sensitive --long-branch -Wall` plus
the target output option such as `--cbm-prg`. For manual assembly:

```bash
64tass --ascii --case-sensitive --long-branch -Wall --cbm-prg \
    -o myprogram.prg myprogram.asm
```

For debugging, add `--vice-labels --labels=labels.txt` and/or
`--list=listing.txt`.
