---
name: asm6502-coder
description: Write 6502/65C02 assembly code using 64tass syntax, for use within Prog8 programs
license: MIT
compatibility: opencode
---

# 65(C)02 Assembly Coder Skill

You are writing **6502/65C02 assembly** using **64tass syntax**, in separate `*.asm` files or embedded in a Prog8 program (inside `%asm {{ }}` blocks or `asmsub` routines). Follow all rules below.

## Git Operations
- When moving, renaming, or deleting git-tracked files, **always use `git mv` or `git rm`** instead of plain `mv`/`rm`. This preserves history and properly stages the change. Plain `mv`/`rm` causes git to see them as delete+add (losing history).

## Debugging Generated Assembly
- The prog8 compiler outputs `*.asm` (assembly source) and (with -asmlist option) also `*.list` (full listing with address/symbols) when compiling. Inspect these to debug generated code, verify optimizations, and trace instruction sequences.

## Assembler: 64tass Syntax
- **NOT ca65/cc65** or other assemblers. Key differences:
- `.proc` / `.pend` for procedures (scoping)
- `_label` for local labels (prefixed with underscore, scoped to `.proc`)
- **Symbol aliases**: inside a `.proc`, use `_name = P8ZP_SCRATCH_B1` to give a scratch variable a descriptive name.
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
- Data directives: `.byte`, `.word`, `.dword`, `.fill` (allocate space)
- Equates: `label = value` (not `label .equ value` or `#define`)
- **Number Literals**: Hex `$1234`, Binary `%10101010`, Decimal `123`.
- Zero-page variables defined with `=`
- `.text` for inline string data
- **Conditional assembly**: `.if`, `.elsif`, `.else`, `.endif`
- **Memory Sections**: `.section <name>`, `.send <name>` (common sections: `CODE`, `DATA`, `BSS`, `BSS_NOCLEAR`)

## Instructions
- **Instructions like `rol`, `ror`, `asl`, `lsr` (and `inc`, `dec` on 65C02) require an explicit operand for accumulator**: write `rol a`, `inc a`, etc., not just `rol` or `inc`.
- Standard 6502 addressing modes: implied, immediate (`#`), zero-page (`zp`), zero-page,X (`zp,x`), absolute (`abs`), absolute,X (`abs,x`), absolute,Y (`abs,y`), indirect (`(abs)`), indirect,X (`(zp,x)`), indirect,Y (`(zp),y`), relative (branches), accumulator
- Branches: `bne`, `beq`, `bmi`, `bpl`, `bcs`, `bcc`, `bvs`, `bvc` (relative, max +127/-128 bytes)
- Jumps: `jmp` (absolute or indirect), `jsr`/`rts` (subroutine call/return)
- No `push`/`pop` mnemonics — use `pha`/`pla` (byte) and `txa`/`phx`/`plx`/`tay`/`phy`/`ply` for registers

### Instruction Side Effects (Flags)
- **`Z` (Zero)**: Set if the result of an operation is 0.
- **`N` (Negative)**: Set if bit 7 of the result is 1.
- **`C` (Carry)**: Used for unsigned overflow and shifts. `cmp` sets `C` if `Register >= Operand`.
- **`V` (Overflow)**: Set if a signed arithmetic operation overflowed.
- **Commonly affected by**: `lda`, `ldx`, `ldy`, `inx`, `dex`, `tax`, `tay`, `txa`, `tya`, `and`, `ora`, `eor`, `asl`, `lsr`, `rol`, `ror`, `adc`, `sbc`, `cmp`, `cpx`, `cpy`, `bit`.
- **Note**: `lda`, `ldx`, `ldy` do NOT affect the Carry flag. Only `adc`, `sbc`, `cmp`, and shift/rotate instructions affect Carry.

## 6502 vs 65C02
- **CX16 target only**: can use WDC 65C02 instructions — `stz`, `phx`, `plx`, `phy`, `ply`, `bra`, `trb`, `tsb`, `stp`, `wai`, `inc a`, `dec a`, `bit #imm`, `bit zp,x`, `bit abs,x`, `jmp (abs,x)`, and `(zp)` indirect addressing mode (e.g., `lda (zp)`).
- **C64, C128, PET32 targets**: original 6502 only — no `stz`, no `phx`/`plx`/`phy`/`ply`, no `bra` etc.
- **Note**: The Rockwell/bit-manipulation instructions (`rmb`, `smb`, `bbr`, `bbs`) are **NOT** available.
- Check the target before using 65C02-specific instructions
- **6502 / 65C02 instruction reference table**: https://www.pagetable.com/c64ref/6502/?cpu=65c02&tab=4 (provides exact instruction details for all opcodes: operation, addressing modes, byte length, and cycle count).

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
- **CRITICAL**: Parameter names in `asmsub` are **documentation only**. You MUST use the actual registers in your assembly code, NOT the parameter names (unless you create aliases yourself).
- Create symbolic aliases at assembly top for clarity: `x1 = cx16.r0`, `y1 = cx16.r0L`
- Accessing Prog8 parameters if they were NOT mapped to registers: use `p8v_paramname`. (Mapping to registers is preferred for speed).

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

### Common Branch Logic (Comparisons)
| Logic | Unsigned | Signed |
|-------|----------|--------|
| `A == imm` | `cmp #imm`, `beq label` | (Same) |
| `A != imm` | `cmp #imm`, `bne label` | (Same) |
| `A < imm` | `cmp #imm`, `bcc label` | `sec`, `sbc #imm`, `bvc *+4`, `eor #$80`, `bmi label` |
| `A >= imm` | `cmp #imm`, `bcs label` | `sec`, `sbc #imm`, `bvc *+4`, `eor #$80`, `bpl label` |
| `A <= imm` | `beq label`, `bcc label` | (Use complex signed logic or reorder) |
| `A > imm` | `beq +`, `bcs label`, `+` | (Use complex signed logic or reorder) |

### Looping (downto with BNE)
```asm
        ldx #count
loop    ; do work here
        dex
        bne loop        ; loop while X != 0 (runs 'count' times)
```

### Self-Modifying Code (SMC) Detection
Look for `sta`, `stx`, or `sty` pointing into code labels:
```asm
        lda #$42
        sta _target+1   ; Modifies the immediate operand of the LDA at _target
        ...
_target lda #$00        ; This #$00 will be replaced by #$42 at runtime
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

## CPU Quirks and Pitfalls

### JMP ($xxFF) Page Wrap Bug (NMOS 6502)
- **Problem**: On original 6502 CPUs, `jmp ($caff)` will fetch the LSB from `$caff` but the MSB from `$ca00` (instead of `$cb00`).
- **Target**: Affects C64, C128, PET32.
- **Solution**: Avoid placing indirect jump vectors on a page boundary, or use the CX16 (65C02) which fixed this bug.

### BRK Instruction and the "Signature Byte"
- **Behavior**: After a `BRK` instruction, the return address on the stack is incremented by **2**. This means the CPU skips the byte immediately following the `BRK` opcode.
- **Usage**: This skipped byte is often used as a "signature" or parameter byte for the BRK handler.

### BIT Instruction Flags
- **Absolute/Zero-page**: `bit $1234` copies bit 7 of the memory value to the **N** flag and bit 6 to the **V** flag. The **Z** flag is set based on `A AND memory`.
- **Immediate (65C02 only)**: `bit #$01` only affects the **Z** flag; it does **NOT** modify N or V.
- **NMOS 6502**: Does **not** support `bit #imm`.

### The "B" (Break) Flag
- **Quirk**: The B flag (bit 4 of the status register) doesn't actually exist in the hardware status register. It only exists on the stack after a `PHP` or `BRK` instruction (set to 1) or a hardware IRQ/NMI (set to 0).
- **Detection**: To tell if an interrupt was caused by `BRK` or a hardware IRQ, your handler must `pla`, `and #$10`, and check the result.

### Decimal Mode Flag (D) Persistence
- **Pitfall**: On NMOS 6502, the `D` flag is **not** cleared on interrupt. Always use `cld` in IRQ handlers. On 65C02, it is cleared automatically, but `cld` is still good practice.
- **ADC/SBC**: Be extremely careful with arithmetic if you haven't explicitly set or cleared the `D` flag, as its state might be unknown.

## Optimization Tips
- **`stz` (65C02 only)**: Saves cycles and bytes compared to `lda #0`, `sta ...`.
- **`bra` (65C02 only)**: Shorter and usually faster than `jmp`.
- **Avoid `clc` before `bcc`**: `cmp` already sets the carry flag correctly for `bcc`/`bcs`.
- **`inx` / `dex` vs `clc`+`adc #1`**: Incrementing/decrementing is faster and doesn't affect the carry flag.
- **Zero-page usage**: Accessing variables in zeropage is 1 cycle faster and 1 byte shorter than absolute addressing.
- **Cycle Counting**: Most instructions take 2-4 cycles. `jsr` takes 6, `rts` takes 6. Branches take 2 (no branch), 3 (branch taken), or 4 (branch taken across page boundary).

## 64tass Macros
Define macros for common tasks:
```asm
pushax  .macro
        pha
        txa
        pha
        .endm

popax   .macro
        pla
        tax
        pla
        .endm
```
Call them with `#pushax` and `#popax`.

## IRQ Handler Best Practices
- **Clear the Decimal Flag (`cld`)**: On the original 6502, the decimal flag (`D`) is **not** automatically cleared when an interrupt occurs. If the interrupted code was in decimal mode, your handler will also run in decimal mode, causing arithmetic errors. Always call `cld` at the beginning of your handler. The 65C02 clears it automatically, but `cld` is still recommended for portability.
- Keep handlers extremely short and fast — they run with interrupts disabled and steal cycles from the main program.
- Do NOT do lengthy processing, I/O, or complex subroutine calls inside the handler.
- Instead, set a boolean flag or semaphore that the main loop checks periodically, and do the actual work there.

## Invoking the Assembler (64tass)
If you need to manually invoke `64tass` to assemble a generated `.asm` file, you should be aware of the default arguments that `prog8c` supplies to ensure compatibility with the generated code:

- `--ascii`: **CRITICAL.** Prog8 generates character and string data in ASCII. Without this flag, `64tass` defaults to PETSCII, which will garble your strings.
- `--case-sensitive`: Prog8 is case-sensitive and expects the assembler to be as well.
- `--long-branch`: Enables automatic conversion of relative branches (`beq`, `bne`, etc.) to absolute jumps if the target is out of range. Prog8 relies on this.
- `-Wno-implied-reg`: Suppresses warnings when the accumulator `a` is omitted from instructions like `rol`, `lsr`, etc. (though the skill recommends always using `rol a` for clarity).
- `-Wall`: Enables all warnings.
- `--cbm-prg` (or `--atari-xex` / `--nostart`): Sets the output format and adds the appropriate load address header.

### Optional but Recommended for Debugging:
- `--vice-labels --labels=labels.txt`: Generates a label file that can be loaded into the VICE monitor (`load_labels "labels.txt"`) to see your Prog8 symbol names while debugging.
- `--list=listing.txt`: Generates a full assembly listing file with addresses and opcodes.

### Example manual invocation:
```bash
64tass --ascii --case-sensitive --long-branch -Wall --cbm-prg -o myprogram.prg myprogram.asm
```
