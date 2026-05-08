---
name: asm6502-coder
description: Write 6502/65C02 assembly code using 64tass syntax, for use within Prog8 programs
license: MIT
compatibility: opencode
---

# 65(C)02 Assembly Coder Skill

You are writing **6502/65C02 assembly** using **64tass syntax**, in separate `*.asm` files or embedded in a Prog8 program (inside `%asm {{ }}` blocks or `asmsub` routines). Follow all rules below.

## Debugging Generated Assembly
- The prog8 compiler outputs `*.asm` (assembly source) and (with -asmlist option) also `*.list` (full listing with address/symbols) when compiling. Inspect these to debug generated code, verify optimizations, and trace instruction sequences.

## Assembler: 64tass Syntax
- **NOT ca65/cc65** or other assemblers. Key differences:
- `.proc` / `.pend` for procedures (scoping)
- `_label` for local labels (prefixed with underscore, scoped to `.proc`)
- **Anonymous labels**: defined as `+` (forward) or `-` (backward) at the start of a line. Reference them in branches using `+`, `++`, `+++` etc. (first/second/third *upcoming* anonymous forward label) and `-`, `--`, `---` etc. (first/second/third *preceding* anonymous backward label).
  - Crucial: `+` refers to the NEXT upcoming `+` label, `++` refers to the ONE AFTER that, etc.
  - `-` refers to the MOST RECENT `-` label, `--` to the one before that, etc.
  - When a `+` label is passed, the forward-reference count resets (so `++` then refers to the next one after that new label)
  - Example:
    ```asm
    -   dex         ; backward label '-'
        bne -       ; branch to most recent '-' (the dex above)
        ldx #5
    +   dex         ; forward label '+'
        bne +       ; branch to this same '+' (forward)
        sta $400
    +   lda #0      ; second '+'
        bne ++      ; branch to the '++' below
        rts
    ++  inc $d020   ; third forward label
    ```
- Data directives: `.byte`, `.word`, `.dword`
- Equates: `label = value` (not `label .equ value` or `#define`)
- Zero-page variables defined with `=`
- `.text` for inline string data

## Instructions
- **Instructions like `rol`, `ror`, `asl`, `lsr`, `php`, `pla` require an explicit operand for accumulator**: write `rol a`, `ror a`, not just `rol`/`ror`
- Standard 6502 addressing modes: implied, immediate (`#`), zero-page (`zp`), zero-page,X (`zp,x`), absolute (`abs`), absolute,X (`abs,x`), absolute,Y (`abs,y`), indirect (`(abs)`), indirect,X (`(zp,x)`), indirect,Y (`(zp),y`), relative (branches), accumulator
- Branches: `bne`, `beq`, `bmi`, `bpl`, `bcs`, `bcc`, `bvs`, `bvc` (relative, max +127/-128 bytes)
- Jumps: `jmp` (absolute or indirect), `jsr`/`rts` (subroutine call/return)
- No `push`/`pop` mnemonics — use `pha`/`pla` (byte) and `txa`/`phx`/`plx`/`tay`/`phy`/`ply` for registers

## 6502 vs 65C02
- **CX16 target only**: can use 65C02 instructions — `stz`, `phx`, `plx`, `phy`, `ply`, `bra`, `trb`, `tsb`, `stp`, `wai`, `clr`, `ina`, `dea`, `cmp (zp)`, `dec`/`inc abs,x` etc.
- **C64, C128, PET32 targets**: original 6502 only — no `stz`, no `phx`/`plx`/`phy`/`ply`, no `bra`
- Check the target before using 65C02-specific instructions

## Calling Convention / Register Conventions
- **Accumulator (A)**: 8-bit, used for most arithmetic, data movement, return values
- **X register**: 8-bit, often used for indexing, loop counters
- **Y register**: 8-bit, often used for indirect addressing index
- **Processor Status (P)**: flags — carry (C), zero (Z), negative/N (sign bit 7), overflow (V), decimal (D), interrupt (I), break (B)
- **No caller-saved vs callee-saved convention** — list all modified registers in `clobbers (A, X, Y)` when writing `asmsub`
- The CPU stack (SP, $0100-$01FF) is limited (usually ~128 bytes free). Do not overflow it

## Assembly within Prog8 Programs

### `%asm {{ }}` blocks
- Embed arbitrary 64tass assembly directly in your Prog8 source
- Access Prog8 symbols using their prefixed names (see below)
- Can be placed inside subroutines or at block level

### `asmsub` (assembly subroutine)
- For kernel (ROM) routines or low-level assembly
- Parameters passed via registers: `@A`, `@X`, `@Y`, `@AX` (A low, X high), `@AY` (A low, Y high), `@R0`-`@R15`, `@FAC1`/`@FAC2` (float), `@Pc` (carry), `@Pz` (zero)
- Return value: `-> type @register` — also via `@Pz`/`@Pc` for flags
- Clobbers: `clobbers (A, X, Y)` — MUST list all modified registers
- **Parameter names are documentation only** — use the actual registers in assembly, NOT parameter names
- Create symbolic aliases at assembly top for clarity: `x1 = cx16.r0`, `y1 = cx16.r0L`

Example:
```prog8
asmsub line(uword x1 @R0, ubyte y1 @A, uword x2 @R1, ubyte y2 @Y) clobbers (A, X, Y) {
    %asm {{
        x1 = cx16.r0
        x2 = cx16.r1
        lda  x1        ; use alias, not "_x1"
    }}
}
```

### `asmsub` parameter annotation reference
| Annotation | Register | Size |
|------------|----------|------|
| `@A` | Accumulator | 8-bit |
| `@X` | X register | 8-bit |
| `@Y` | Y register | 8-bit |
| `@AX` | A (low) + X (high) | 16-bit |
| `@AY` | A (low) + Y (high) | 16-bit |
| `@R0`-`@R15` | cx16 virtual registers | 16-bit each |
| `@FAC1`/`@FAC2` | Floating-point accumulators | 5-byte float |
| `@Pc` | Carry flag | bool |
| `@Pz` | Zero flag | bool |

## Accessing Prog8 Symbols from Assembly
All Prog8 symbols are prefixed when accessed from assembly:

| Prefix | Refers to | Example |
|--------|-----------|---------|
| `p8v_` | Variables, parameters | `p8v_myvar` |
| `p8s_` | Subroutines | `p8s_mysub` |
| `p8b_` | Blocks | `p8b_myblock` |
| `p8c_` | Constants, enum members | `p8c_myconst`, `p8c_MyEnum_Member` |
| `p8l_` | Labels | `p8l_mylabel` |
| `p8t_` | Struct types | `p8t_MyStruct` |
| `p8_` | Other symbols | |

- **Fully qualified**: e.g., `p8b_myblock.p8v_myvar`, `p8b_myblock.p8s_mysub.p8v_localvar`
- Within a `.proc` (subroutine) in your assembly, short names often work (assembler scoping)
- **`%option no_symbol_prefixing`**: disables all prefixes. Stdlib modules (`cbm`, `cx16`, `txt`) use this — you can write `cbm.CHROUT` directly
- **Split word arrays**: two separate byte arrays — append `_lsb` and `_msb` to the name: `p8v_myarray_lsb`, `p8v_myarray_msb`

## Zeropage Usage
- Do NOT use arbitrary zeropage locations. Only use these predefined scratch variables:
  - `P8ZP_SCRATCH_B1` (byte)
  - `P8ZP_SCRATCH_REG` (byte)
  - `P8ZP_SCRATCH_W1` (word)
  - `P8ZP_SCRATCH_W2` (word)
  - `P8ZP_SCRATCH_PTR` (word)
- On CX16: virtual registers `cx16.r0`-`cx16.r15` are in zeropage (and their low/high bytes: `cx16.r0L`, `cx16.r0H`, etc.)
- Virtual registers `cx16.r0`-`cx16.r15` are available on ALL targets, but only on CX16 in zeropage
- Assume the scratch variables are not consecutive in zeropage. The CX16 virtual registers ARE consecutive in memory though.
- For additional temporary storage, allocate regular variables in BSS

## Common 6502 Patterns

### Looping (downto with BNE)
```asm
        ldx #count
loop    ; do work here
        dex
        bne loop        ; loop while X != 0
```

### Indirect indexed read (table of data)
```asm
        ldy #index
        lda (ptr),y     ; read byte at address stored in zp ptr + Y
```

### 16-bit arithmetic (word add)
```asm
        clc
        lda word1_lo
        adc word2_lo
        sta result_lo
        lda word1_hi
        adc word2_hi
        sta result_hi
```

### Calling a Prog8 subroutine from assembly
```asm
        jsr p8s_myblock.p8s_mysub
```

### Reading a word variable
```asm
        lda p8v_myword          ; loads LSB
        ldy p8v_myword+1        ; loads MSB (word variables are stored LSB-first)
```
