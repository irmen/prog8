---
name: asm6502-coder
description: Writing or understanding 6502/65C02 assembly in 64tass syntax, standalone or embedded in Prog8
---

# 6502 Assembly Coder

Use 64tass syntax, not ca65 or another assembler. Keep answers concise and
practical. This applies to standalone `.asm` files and Prog8 `%asm {{ }}` or
`asmsub` blocks.

Reference documentation for the assembler is available locally in
`~/Documents/prog8-dev-resources` (the 64tass manual, `64tass.md`). Search this
folder first before looking online.

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

## Reference

### Primary: 6502 Family CPU Reference

<https://www.pagetable.com/c64ref/6502/?cpu=65c02&tab=2> is the reference to reach for.
It covers the whole 6502 family and lets you **select the CPU variant**, which matters
because the parts genuinely differ. The variants are `6502`, `6502rorbug`, `65dtv02`,
`65c02`, `r65c02` (Rockwell), `65c02s`, `65ce02` and `65c816`. Change the `cpu=` query
parameter to switch; for Prog8, use `cpu=65c02` when working on cx16 and `cpu=6502` for
c64, c128 and pet32.

Per instruction it gives the operation, the opcodes, the byte length, the cycle count
and which status flags are affected, with tabs for opcodes, instructions, addressing
modes and a full opcode table (switchable between 4-4 and 3-3-2 layout). It can also
show the NMOS undocumented opcodes, and each part lists what it is based on, so a
variant's inherited instructions are visible.

Cycle counts use a compact notation worth knowing: a trailing `t` means +1 if a branch
is taken, `p` means +1 if a page is crossed, and `d` means +1 when the D (decimal) flag
is set. So `d0  2+t+p` for `bne` is 2 cycles, 3 if taken, 4 if taken across a page.

Consult it whenever an exact figure is needed rather than recalled: the byte length or
cycle cost of an instruction, which flags it clobbers, whether an addressing mode exists
on the target CPU, or whether an instruction belongs to a different part at all. Typical
reasons are costing a peephole or code-size tradeoff, checking a hand-written routine,
and confirming a mnemonic is valid before using it.

Do not assume a timing from memory or from another part. Cycle counts are exactly the
kind of detail that is misremembered, and the difference matters when it is the basis of
an optimization claim. Read the number off the reference for the specific CPU being
targeted.

### Secondary: WDC 65C02 reference

<https://cx16.dk/65c02/reference.html> is a 65C02-only reference. It is worth having
because it states each instruction's effect in a single formula (for example
`ADC:  A,Z,C,N = A+M+C`), which makes the flag behaviour quick to check, and it lists
every status bit per instruction including the ones not affected. Use it for flag
semantics; use the primary reference for timings and for anything CPU-specific, since
it covers the 65C02 only and will not answer a question about the NMOS 6502.

### Neither reference covers the assembler

Both document the CPU, not 64tass. For whether 64tass accepts a mnemonic or a given
syntax, assemble it: an instruction outside the selected CPU set fails with
`error: general syntax` rather than being silently ignored. Note also that 64tass has
no `w65c02s`; its CPU selection is `--m65xx` (default), `--m65c02` and `--m65ce02`, and
Prog8 emits `.cpu 'w65c02'`.

## Tools

Prog8 invokes 64tass with `--ascii --case-sensitive --long-branch -Wall` plus
the target output option such as `--cbm-prg`. For manual assembly:

```bash
64tass --ascii --case-sensitive --long-branch -Wall --cbm-prg \
    -o myprogram.prg myprogram.asm
```

For debugging, add `--vice-labels --labels=labels.txt` and/or
`--list=listing.txt`.

To check an instruction's encoding or size, assemble it in isolation and read the
output bytes. The 2-byte load address comes first, so `stx $7b` / `ldy $7b` below
shows as `86 7b a4 7b` - two instructions, four bytes:

```bash
printf '.cpu  %s\n* = $0801\nlbl\tstx\t$7b\n\tldy\t$7b\n' "'w65c02'" > sz.asm
64tass --ascii --case-sensitive --cbm-prg -o sz.prg sz.asm && xxd sz.prg
```

Note that a 64tass label takes no trailing colon; `lbl:` is a syntax error reported as
`error: label required`.
