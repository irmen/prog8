---
name: m68k-coder
description: Writing or understanding Motorola 68000/68020 assembly code using vasm mot syntax, for use as stand alone .asm or inline within Prog8 programs
---

# M68K Assembly Coder Skill

You are an expert Motorola 68000 assembly development assistant. Keep responses concise and practical - prefer short, correct code examples over lengthy prose.
You are working with **M68K assembly** using **vasm mot (Motorola) syntax**, in separate `*.asm` files or embedded in a Prog8 program (inside `%asm {{ }}` blocks or `asmsub` routines). Follow all rules below.

## Assembler: vasm mot Syntax
### Formatting Rules
- Labels start in the first column of the line.
- Instructions are indented at least 4 spaces.
- Two spaces between the instruction opcode and its operand (e.g., `move.l  d0,a0`, `addq.w  #1,d0`).
- End-of-line comments are preceded by two spaces before the `;` (e.g., `move.w #$100,d0  ; load 256`).
- If a comment is the only thing on a line, it starts in the first column (no indentation).
- Opcodes and operands are written in lowercase (e.g., `move.l`, not `MOVE.L`).
- vasm prefers NO space after the comma between operands (but with `-spaces` flag it allows it).

- **NOT Devpac, PhxAss, or other assemblers**. Key differences:
- `SECTION` for memory sections (not `SECTION` with different syntax)
- Local labels prefixed with `.` (e.g., `.loop`, `.done`)
- **Symbol aliases**: use `=` for equates (e.g., `myconst = $100`)
- Data directives: `dc.b`, `dc.w`, `dc.l` (define constant byte/word/long)
- Space allocation: `ds.b`, `ds.w`, `ds.l` (define space byte/word/long)
- **Number Literals**: Hex `$1234`, Binary `%10101010`, Decimal `123`.
- Character literals: `#'A'` for ASCII value
- **Conditional assembly**: `if`, `else`, `endif` (or `IFEQ`, `IFNE`, etc.)
- **Memory Sections**: `SECTION .text,code`, `SECTION .data,data`, `SECTION .bss,bss`

## Register Set and Addressing Modes
For complete details on M68K registers (D0-D7, A0-A7, PC, SR, USP, SSP) and addressing modes (register direct, immediate, absolute, indirect, indexed, PC-relative), see the [Motorola M68000 Programmer's Reference Manual](https://www.nxp.com/files-static/archives/doc/ref_manual/M68000PRM.pdf).

Key points:
- **32-bit architecture**: All registers (D0-D7, A0-A7) are 32 bits wide
- **Pointers are 4 bytes**: Unlike 6502 targets where pointers are 2 bytes (`uword`), M68K pointers are 4 bytes (`pointer` or `long`). When writing assembly, always use `.l` suffix for pointer operations
- **Big-endian**: M68K stores most-significant byte first (opposite of 6502)
- **Word alignment**: Prefer word-aligned access for performance
- **Stack operations**: `move.l d0,-(sp)` pushes, `move.l (sp)+,d0` pops
- **A7** is the stack pointer (SP)

## Instructions
For a complete list of M68K instructions, see the [Motorola M68000 Programmer's Reference Manual](https://www.nxp.com/files-static/archives/doc/ref_manual/M68000PRM.pdf)

### Instruction Size Suffixes
- `.b` - Byte (8 bits)
- `.w` - Word (16 bits)
- `.l` - Long (32 bits)
- Default size varies by instruction (e.g., `move` defaults to `.w`, `addq` defaults to `.b`)

## 68000 vs 68020
### 68000 (amiga500 target)
- 16-bit external data bus, 24-bit address bus
- 16-bit multiply: `mulu.w`, `muls.w` (16x16->32)
- 16-bit divide: `divu.w`, `divs.w` (32/16->16 quotient + 16 remainder)
- Limited addressing modes (no scaled indexing)
- No 32-bit multiply/divide instructions
- No bit field instructions
- No byte swap instruction

### 68020 Additions (qemu68k target)
- 32-bit address bus (full 32-bit addressing)
- 32-bit multiply: `mulu.l`, `muls.l` (32x32->32 or 32x32->64)
- 32-bit divide: `divu.l`, `divs.l` (32/32->32)
- `divul.l` for separate quotient/remainder
- Scaled indexing: `d8(An,Xn*size)` where size is 1, 2, 4, or 8
- Bit field instructions: `bfchg`, `bfclr`, `bfexts`, `bfextu`, `bfffo`, `bfins`, `bfset`, `bftst`
- `byteSwap` instruction: `bkpt` (not commonly used)
- Improved addressing modes and instruction encoding

### Code Generation Notes
- Use `ext.w` then `ext.l` for byte-to-long sign extension on 68000
- Use `extb.l` for direct byte-to-long on 68020+
- 68000 cannot save/restore status bits with non-privileged instructions (PUSHST/POPST IR opcodes fail)

## Calling Convention / Register Conventions
### Standard M68K Calling Convention
- **D0**: Primary return value
- **D1**: Secondary return value
- **D2-D7**: Caller-saved (must be preserved if used)
- **A0**: Secondary return value (for pointers)
- **A1**: Temporary
- **A2-A5**: Caller-saved
- **A6**: Frame pointer (optional)
- **A7**: Stack pointer (SP)
- **D0-D1, A0-A1**: Typically used for scratch/temps
- **A2-A6, D2-D7**: Should be preserved across calls (callee-saved)

### Prog8 Integration
- Return values in D0 (slot 10), D1 (slot 11), D2 (slot 12)
- Parameters passed via registers or stack
- List modified registers in `clobbers` for `asmsub`

## Assembly within Prog8 Programs

### `%asm {{ }}` blocks
- Embed arbitrary vasm assembly directly in your Prog8 source
- Access Prog8 symbols using their prefixed names (see below)
- Can be placed inside subroutines or at block level

### `asmsub` (assembly subroutine)
- For kernel (ROM) routines or low-level assembly
- Parameters passed via registers: `@D0`-`@D7`, `@A0`-`@A6`, `@R0`-`@R15`
- Return value: `-> type @register` - also via `@Pz`/`@Pc` for flags
- Clobbers: `clobbers (D0, D1, A0, A1)` - MUST list all modified registers
- **CRITICAL**: Parameter names in `asmsub` are **documentation only**. You MUST use the actual registers in your assembly code, NOT the parameter names.

Example:
```prog8
asmsub myfunc(uword value @D0, uword result @A0) clobbers (D0, A0) {
    %asm {{
        move.l  d0,d1       ; use actual register names
        add.l   #$100,d1
        move.l  d1,(a0)     ; store result
    }}
}
```

### `asmsub` parameter annotation reference
| Annotation | Register | Size |
|------------|----------|------|
| `@D0`-`@D7` | Data registers | 32-bit |
| `@A0`-`@A6` | Address registers | 32-bit |
| `@R0`-`@R15` | Virtual registers (mapped to D0-D7, A0-A6) | 32-bit |
| `@FP0`-`@FP7` | FPU registers | 64-bit float |
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
- **`%option no_symbol_prefixing`**: disables all prefixes. Stdlib modules use this.
- **Big-endian byte order**: Variables are stored MSB-first (opposite of 6502)
  - Word (2 bytes): LSB at offset +1, MSB at offset +0
  - Pointer/Long (4 bytes): LSB at offset +3, MSB at offset +0
  - For arrays, use appropriate indexing

## Common M68K Patterns

### Loop with DBRA (Decrement and Branch)
```asm
        move.w  #count-1,d0    ; DBRA runs N+1 times (counts down to -1)
.loop   ; do work here
        dbra    d0,.loop       ; decrement D0, branch if not -1
```

### 32-bit Addition
```asm
        move.l  val1,d0
        add.l   val2,d0
        move.l  d0,result
```

### String Copy (null-terminated)
```asm
        lea     source,a0
        lea     dest,a1
.copy   move.b  (a0)+,(a1)+
        bne.s   .copy          ; branch if not zero
```

### Calling a Prog8 subroutine from assembly
```asm
        jsr     p8s_myblock.p8s_mysub
```

### Reading a word variable (big-endian)
```asm
        move.w  p8v_myword,d0      ; loads full word (MSB at +0, LSB at +1)
        move.b  p8v_myword+1,d1    ; loads LSB only
```

### Reading a pointer (4 bytes, big-endian)
```asm
        move.l  p8v_myptr,a0       ; loads full 32-bit pointer
        move.b  p8v_myptr+3,d0     ; loads LSB only (byte at offset +3)
```

## CPU Quirks and Pitfalls

### Big-Endian Byte Order
- **CRITICAL**: M68K is big-endian (MSB first), opposite of 6502
- Word `$1234` is stored as `$12` at lower address, `$34` at higher address
- When accessing individual bytes of a word, remember the offset is reversed

### Address Register Indirect and Alignment
- **68000**: Word/long access to odd addresses causes address error exception
- **68020+**: Handles misaligned access (with performance penalty)
- Always ensure word/long data is word-aligned

### DIVS/DIVU Overflow
- **Problem**: Division overflow or divide-by-zero sets the V flag and leaves result undefined
- **Solution**: Check divisor for zero before division, check V flag after

### MULS Sign Extension
- `muls.w` sign-extends both operands before multiplying
- `mulu.w` treats both as unsigned
- Result is always 32-bit in destination register

### MOVE vs MOVEA
- `move.l` sets condition codes
- `movea.l` does NOT set condition codes (address register variant)
- Use `move.l` when you need to test the result

### Stack Alignment
- Stack pointer (A7) should be word-aligned
- Pushing odd-sized data can misalign the stack
- Always push words or longs, not bytes

## Optimization Tips
- **`addq`/`subq`**: Faster than `add`/`sub` for constants 1-8
- **`moveq`**: Fastest way to load small constants (-128 to 127)
- **`lea`**: Load effective address is faster than `move.l #imm,an`
- **Register usage**: Prefer data registers over memory access
- **Loop unrolling**: Use `dbra` with pre-decremented counter
- **`clr.x`**: Faster than `move.x #0` for zeroing registers
- **Word alignment**: Align data structures for faster access
- **`ext.w` + `ext.l`**: Two-step sign extension on 68000
- **`extb.l`**: Direct byte-to-long on 68020+ (one instruction)

## Invoking the Assembler (vasmm68k_mot)
If you need to manually invoke `vasmm68k_mot` to assemble a generated `.asm` file, you should be aware of the default arguments that `prog8c` supplies:

### For QEMU 68k (68020, ELF format)
- `-m68020`: Target CPU
- `-m68881`: Enable FPU instructions
- `-Felf`: Output ELF object format (then link with `vlink`)
- `-opt-speed`: Optimize for speed
- `-warnunaligned`: Warn on misaligned access
- `-ldots`: Allow dots in labels
- `-spaces`: Allow spaces after commas

### For Raw Binary Output
- `-m68000` or `-m68020`: Target CPU
- `-Fbin`: Output raw binary
- `-join=0x<addr>`: Set load address
- `-opt-speed`, `-warnunaligned`, `-ldots`, `-spaces`: Same as above

### Optional but Recommended for Debugging:
- `-L listing.txt`: Generates a full assembly listing file with addresses and opcodes

### Example manual invocation (QEMU 68k):
```bash
vasmm68k_mot -m68020 -m68881 -Felf -opt-speed -warnunaligned -ldots -spaces -o myprogram.o myprogram.asm
vlink -b elf32m68k -n -T link.ld -o myprogram myprogram.o
```

## Running Programs

### Amiga 500 (vamos)
Prog8 uses **vamos** (part of [Amitools](https://github.com/cnvogelg/amitools)) to run amiga500 programs. When you use `-emu` with the amiga500 target, prog8c automatically invokes:
```bash
vamos --cpu 68000 myprogram
```

Install vamos via your package manager or build from source at the Amitools repository.

### QEMU 68k (qemu-system-m68k)
For the qemu68k target, Prog8 uses **qemu-system-m68k**. When you use `-emu` with the qemu68k target, prog8c automatically invokes:
```bash
qemu-system-m68k -M virt -cpu m68020 -m 1M -kernel myprogram.elf -nographic
```

Install via your package manager:
```bash
sudo apt install qemu-system-m68k       # Debian/Ubuntu
sudo pacman -S qemu-system-m68k         # Arch Linux
```
