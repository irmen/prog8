# Code Generator Backend Design (IR-based)

This document describes the design of a code generator backend for the Prog8 compiler,
targeting 6502 (and eventually 68000). It focuses on the abstract concepts and conventions
that a new IR-based code generator should implement.

> **Warning:**
>
> The **old** 6502 code generator walks the SimpleAST (`PtProgram`, `PtAssignment`,
> `PtFunctionCall`, etc.) to emit assembly. **A new code generator MUST NOT do this.**
>
> Instead, it must work exclusively from the **.p8ir IR file**. The IR is fundamentally
> different from the AST -- it is a linear sequence of instructions (load, store, add,
> branch, call, etc.) grouped into chunks, operating on virtual registers and memory
> locations. The code generator's job is to translate each IR instruction into the
> target's equivalent instructions, not to walk AST nodes.
>
> The .p8ir file contains every detail needed to generate equivalent assembly:
> memory layout, symbol definitions, subroutine chunks with their instruction bodies,
> register allocations, and data sections. The IR code generator and virtual machine
> demonstrate this approach -- study those modules rather than the old SimpleAST-based
> code generator.

## 1. Entry Point

The code generator implements the `ICodeGeneratorBackend` interface:

```kotlin
interface ICodeGeneratorBackend {
    fun generate(program: PtProgram,
                 symbolTable: SymbolTable,
                 options: CompilationOptions,
                 errors: IErrorReporter
    ): IAssemblyProgram?
}
```

It receives the full program AST (already analyzed and optimized), a symbol table,
compilation options, and an error reporter. It produces an `IAssemblyProgram`
which can be assembled into a binary.

> **Note:**
> The above interface is used by the **old** SimpleAST-based code generators.
> A new IR-based code generator will likely need a different entry point that takes
> the parsed IR file (`IntermediateProgram`) instead of a `PtProgram` AST.

## 2. Memory Sections

The generated assembly organizes data into these sections:

**Constants**
  Read-only values embedded in the code section. Includes numeric constants
  (simple value equates), float constants (emitted as raw bytes), and memory
  slab data blocks.

**Zero-Page Variables**
  Fast-access zero-page RAM (addresses $02-$FF). Used for frequently accessed
  scalar variables. Allocation decisions are based on:
  - Explicit `%zeropage` directive (`require` / `prefer` / `dontcare` / `not`)
  - Usage frequency scoring (weighted by type size and loop nesting depth)
  - Split-word arrays of 2-byte elements can be stored as two separate byte
    arrays (LSB and MSB halves) for efficient indexed access with no scaling.

**BSS (Uninitialized Data)**
  Zero-initialized variables in regular RAM. This section is cleared at program
  startup. Includes:
  - Module-level and local variables without initializers
  - The virtual register file (`p8_regfile`, 400 bytes for 200 word-sized registers)
  - `dirty` variables (not required to be consistent across subroutine calls)
  - Memory slabs (runtime-allocated blocks, in sub-section `BSS_SLABS`)

**BSS_NOCLEAR**
  A sub-section of BSS that is NOT zeroed at startup or on subroutine entry.
  Used for:
  - `dirty` variables (explicitly marked as undefined on subroutine entry)
  - Float evaluation temporary variables
  - Any variable where the runtime cost of zeroing is not justified

  The memory is still allocated in RAM, but its initial content is undefined.

**BSS Relocation (Golden RAM / High Bank)**
  BSS variables can be relocated to alternative memory regions:
  - **Golden RAM**: A special RAM area on some targets (e.g., C128) that persists
    across system resets
  - **High Bank**: Extended RAM bank on targets that support banked memory (e.g., CX16)

  When relocation is active via `options.varsGolden` / `options.varsHighBank`
  and `options.slabsGolden` / `options.slabsHighBank`:
  - `BSS_NOCLEAR` and `BSS_SLABS` are emitted at the non-relocated location
  - `prog8_program_end` is emitted before the relocated sections
  - An `* = relocatedStart` directive switches to the relocated area
  - `BSS` is emitted in the relocated area with an overflow check
  - A `PROG8_VARSHIGH_RAMBANK` equate defines the active RAM bank

**Initialized Data**
  Data with compile-time initial values. Only arrays and strings can have
  non-zero initializers; scalar numeric variables with non-zero initial values
  are initialized via runtime assignment statements.

**Struct Instances**
  Statically allocated struct instances, with optional initial values.
  - Instances **without** initial values go into the `BSS` section (zeroed at startup)
  - Instances **with** initial values go into the `STRUCTINSTANCES` section
    (initialized data, inline with the program)
  - Struct type definitions (`IRStStructDef` from the IR symbol table) are emitted
    as 64tass `.struct` blocks with named fields and parameterized constructors,
    enabling `.dstruct` directives in data emission

**Variable Alignment**
  Variables may have an alignment constraint (`align: UInt` in
  `IRStStaticVariable` and `IRStMemorySlab`). When `align > 1`,
  a `.align N` directive is emitted before the variable allocation.
  This ensures the variable starts at an address that is a multiple of N.
  Alignment only applies to non-zero-page variables (strings and arrays).

**Split-Word Arrays**
  Arrays of 2-byte elements (words, pointers) may be stored in "split" layout
  (`DataType.isSplitWordArray`) as two separate byte arrays:
  - `variable_lsb`: the low bytes of each element
  - `variable_msb`: the high bytes of each element

  This enables indexed access with a simple 8-bit index (no scaling by 2).
  In the generated assembly, split-word arrays with initial values emit a
  helper array directive (`_array_$varname := ...`) followed by `_lsb`
  and `_msb` labels with `<` / `>` byte-selectors.
  Uninitialized split-word arrays are emitted as two `.fill` allocations.

**Memory Slabs**
  Runtime-allocated memory blocks (`IRStMemorySlab`). Emitted in the
  `BSS_SLABS` section (which is part of BSS but separated for optional
  relocation). Each slab gets a `.fill` directive with its size.

**Scratch Space**
  A small set of fixed zero-page locations reserved for temporary values during
  expression evaluation (e.g., scratch bytes, scratch words, a scratch pointer).

## 3. Symbol Naming

Symbols are prefixed to avoid collisions with assembler built-in symbols and
to encode their kind:

| Kind     | Prefix | Example                  |
|----------|--------|--------------------------|
| Block    | p8b_   | `p8b_main`               |
| Sub      | p8s_   | `p8b_main.p8s_start`     |
| Variable | p8v_   | `p8b_main.p8v_x`         |
| Constant | p8c_   | `p8c_MYCONST`            |
| Label    | p8l_   | `p8l_mylabel`            |
| Struct   | p8t_   | `p8t_point`              |
| Instance | p8i_   | `p8i_screen`             |

**Scoped names** use the target assembler's dot notation (e.g., `p8b_main.p8s_start`)
to create nested symbol scopes. When generating code inside a subroutine, local
references should use relative/abbreviated names to make the assembly listing
more readable.

Symbol prefixing is performed before code generation by walking the AST and
modifying node names, then constructing a new `SymbolTable` from the prefixed AST.

## 4. Label Naming

Control flow labels use a monotonically increasing sequence number appended to
a descriptive prefix. The sequence counter is preserved across the entire
compilation unit to guarantee uniqueness.

Common label prefixes:
  - `for_loop_N`, `for_end_N` -- for-loop body and exit
  - `repeat_N`, `repeatend_N` -- repeat-loop body and exit
  - `for_modified_N` -- self-modifying comparison values
  - `if_else_N`, `if_end_N` -- conditional branches
  - `short_circuit_skip_N`, `short_circuit_end_N` -- short-circuit evaluation

A label stack tracks the current innermost loop's exit label, used by
`break` / `exit` statements.

## 5. Startup and Initialization

The generated program's entry point performs these steps in order:

**Phase 1: Program Header (`emitHeader` + `emitLauncher`)**
  - Set CPU type directive and encoding
  - Emit zero-page scratch register definitions as equates
  - Emit user-supplied symbol definitions from command line (`-esa`)
  - Generate launcher stub based on `options.output` and `options.launcher`:

    - `PRG` with `BASIC` launcher: generates a BASIC SYS line with
      `prog8_entrypoint` label, `.word` / `.null` directives for the
      BASIC stub
    - `PRG` with `NONE` launcher: raw header with load address
    - `RAW`: raw header with load address
    - `XEX`: Atari XEX header with load address
    - `LIBRARY`: minimal header with `jmp main.start`

  - Emit `prog8_program_start` label at the beginning of the program
  - Emit startup sequence (`emitStartupSequence`):

    - `cld` instruction
    - Save stack pointer for `sys.exit()` (`tsx` / `stx prog8_lib.orig_stackpointer`)
    - Call `init_system` and `init_system_phase2` (unless `noSysInit`)
    - Call `prog8_lib.program_startup_clear_bss` to zero the BSS section
    - Call `run_global_inits` for block-level variable initializers
    - Call the user's `main.start` subroutine
    - On return, jump to `cleanup_at_exit` which calls `sys.poweroff_system`

**Phase 2: Main Entry Point (generated inside the `main.start` subroutine)**
  - Clear the BSS section (zero all uninitialized variables)
  - For each block, call its block-level variable initializer subroutine
  - Block initializers contain the runtime assignment statements for variables
    with non-zero initial values
  - For zero-page arrays/strings with initial values, copy initial data from
    ROM-stored blocks into the zero-page locations
  - Execute the user's program statements

**Phase 3: Program Cleanup**
  - After main returns, jump to cleanup routine (resets system, etc.)
  - A `prog8_program_end` label is emitted at the very end of the generated
    output (after code, data, and BSS sections). This label marks the end of
    the program in memory and is used by the `progend()` library routine and
    memory layout calculations (e.g., `MEMORY TOP`).
  - A **memtop overflow check** is emitted after `prog8_program_end` using
    64tass's `.cerror` directive:
    `.cerror * >= memtopAddress, "Program too long..."`
    This catches program overflow at assembly time.

### 5.1 Subroutine Entry Initialization

There is no dynamic stack frame in the traditional sense. Local variables are
statically allocated in BSS (not on the stack). Their initial values (if any)
are established by assignment statements within the subroutine body.
`Dirty` local variables are explicitly documented as being undefined on
subroutine entry (they get whatever value was left in memory).

## 6. Subroutine Calling Convention

### 6.1 Argument Passing

Arguments are passed via CPU registers when possible, falling back to
passing through static parameter variable slots.

**Optimized register passing (simple integer types):**
  - 1 byte argument: passed in accumulator (A)
  - 2 byte arguments: first in A, second in Y
  - 1 word argument: passed in AY (A=lo, Y=hi)
  - 1 long argument: passed in combined register pair (e.g., virtual registers)

**General case (more arguments or complex types):**
  - Arguments are assigned to the subroutine's statically allocated parameter
    variables before the `jsr` call
  - Complex expressions that might clobber registers are evaluated first,
    simpler register-based arguments last

**External assembly subroutines (`PtAsmSub`) with explicit register specs:**
  - Arguments follow the declared register specification
  - Evaluation order is optimized: combined long regs -> virtual word regs ->
    paired CPU regs -> single CPU regs -> float regs -> carry flag

### 6.2 Return Values

| Type                                   | Return Convention                              |
|----------------------------------------|------------------------------------------------|
| Byte                                   | Accumulator (A)                                |
| Word                                   | Accumulator + Y register (A=lo, Y=hi)          |
| Long                                   | Combined register pair (e.g., virtual registers)|
| Float                                  | Floating-point accumulator (FAC1)              |
| Pointer (address of array/string)      | Accumulator + Y register (A=lo, Y=hi)          |

**Multi-value returns:**
  - First return value goes in A (byte) or AY (word)
  - Subsequent return values use virtual registers (assigned downwards:
    R15, R14, ...)
  - Float returns via FAC1 are assigned *last* to avoid clobbering by
    other return value computations
  - Status flag returns (Carry, Zero, Negative, Overflow) are extracted
    from CPU flags and converted to byte values

### 6.3 Caller/Callee Responsibilities

- **Caller** is responsible for saving/restoring registers that must be
  preserved across the call (notably the X register, which is often used
  as an index base pointer)
- **Callee** may freely use A, X, Y registers, scratch zero-page locations,
  and floating-point accumulators; it is NOT responsible for saving them
- **Static parameters** are assigned by the caller before `jsr`; on entry
  the callee can assume they hold the correct values

### 6.4 Subroutine Types

- **Regular subroutines (`PtSub`)**: Generated as standard `jsr` targets
  with a `rts` return
- **External assembly subroutines (`PtAsmSub`)**: May have explicit register
  parameter specifications; optional `inline` flag causes the body to be
  emitted directly at the call site instead of via `jsr`
- **Banked calls (`JSRFAR`)**: Subroutine address and bank are embedded
  as data words/bytes after the `jsr` instruction

## 7. Expression Evaluation

Expression evaluation uses a tiered strategy:

**Tier 1: Direct (register-based)**
  Simple expressions involving constants, variables, and basic operators
  are evaluated directly using CPU registers and scratch zero-page
  locations. Results remain in registers for immediate use.

**Tier 2: Stack-based fallback**
  Complex expressions (e.g., nested arithmetic, mixed types, float operations)
  are evaluated using a stack discipline on the 6502 hardware stack or via
  pre-allocated temporary variables. The exact strategy depends on the
  target's register set and addressing modes.

**Tier 3: Augmented (in-place) assignments**
  Compound assignment operators (`+=`, `-=`, `*=`, etc.) on variables,
  array elements, memory-mapped locations, and pointer dereferences are
  handled as read-modify-write operations targeting the storage location
  directly, avoiding unnecessary temporary copies.

## 8. Control Flow

**If/Else:**
  Conditional branches test a comparison result (byte, word, long, float)
  against zero. The comparison operator (`==`, `!=`, `<`, `<=`, `>`,
  `>=`) generates the appropriate conditional branch instructions.
  Branch inversion optimization is used: a `beq` over a `jmp` can be
  replaced with `bne` to the target.

**Loops (For, Repeat):**
  - For-loops over numeric ranges generate an index variable, a loop body
    label, an increment/decrement, and a conditional back-branch to the
    loop top
  - For-loops iterating over arrays/strings generate index and pointer
    variables with appropriate bounds checks
  - Repeat-loops are simpler: a loop body label with a back-branch
    (unconditional for `repeat`, conditional for `repeat while/until`)
  - Self-modifying code may be used for comparison immediates in some
    targets (when not in ROMable mode)

**Ternary expressions (`condition ? trueval : falseval`):**
  Generated as a conditional branch around the false branch, with the
  result stored in a common destination register or variable.

## 9. Floating Point

Floating-point operations use the target machine's ROM-based floating-point
routines. Each target platform provides (or must provide) entry points for:
  - Float addition, subtraction, multiplication, division
  - Float-to-integer and integer-to-float conversion
  - Float comparison and sign manipulation
  - The floating-point accumulator (FAC1) holds the primary operand and result

The compiler emits `jsr` calls to these platform-provided routines.
Float constants are emitted as raw bytes matching the target's
floating-point byte format.

## 10. Memory-Mapped I/O

Memory-mapped variables (for hardware registers, etc.) are generated as
simple address equates. Reading from them translates to a load from that
address; writing to them translates to a store to that address.

## 11. Struct Handling

Struct types are emitted as assembler struct definitions (or equivalent),
allowing field access by name with automatic offset calculation.
Struct instances are allocated linearly in memory; fields are laid out
sequentially. Pointer dereferencing through a struct chain (e.g.,
`ptr.field.subfield`) walks the chain of offsets using a scratch pointer
register.

## 12. Multi-Target Considerations (6502 vs. 68000)

The design should accommodate multiple CPU targets:

**Common abstractions:**
  - `Zeropage` or `Fast RAM` concept -- a small, fast address range for
    frequently accessed variables
  - Scratch registers -- fixed temporary storage for expression evaluation
  - CPU register set -- the primary working registers exposed by the ISA
  - Addressing modes -- how memory operands are specified

**Target-specific differences to abstract:**
  - Size and availability of CPU registers (6502: A, X, Y, flags; 68000:
    D0-D7, A0-A7)
  - Stack discipline (6502: hardware stack is limited; 68000: full
    general-purpose stack)
  - Instruction set capabilities (e.g., `STZ` on 65C02, `MUL` on 68000)
  - Floating-point support (ROM routines vs. FPU)
  - Addressing modes (6502: limited; 68000: rich orthogonal set)

### 12.1 6502 vs. 65C02 Instruction Set Differences

The 65C02 is a superset of the 6502. A new code generator should prefer 65C02
instructions when targeting that CPU (the CX16 target uses 65C02). Below are the
instruction choices that differ:

**Zero stores:**
  - 6502: `lda #0` / `sta addr` (2 instructions)
  - 65C02: `stz addr` (1 instruction, does not affect flags)

**Register save/restore (X and Y):**
  - 6502: `txa` / `pha` / `pla` / `tax` (4 instructions)
  - 65C02: `phx` / `plx` (2 instructions, likewise for Y with `phy`/`ply`)

**Accumulator increment/decrement:**
  - 6502: `clc` / `adc #1` (2 instructions, clobbers carry)
  - 65C02: `ina` / `dea` (1 instruction, does not clobber carry)

**Unconditional branch (short range):**
  - 6502: `jmp addr` (3 bytes, 3 cycles)
  - 65C02: `bra addr` (2 bytes, 3 cycles, limited range)

**Zero-page indirect addressing (without Y offset):**
  - 6502: `lda ($zp),y` with `ldy #0` (2 instructions, clobbers Y)
  - 65C02: `lda ($zp)` (1 instruction, direct zero-page indirect)

**Bit manipulation (test and reset/set):**
  - 6502: `lda mask` / `and mask` / `sta addr` or `ora`
  - 65C02: `trb addr` (test and reset bits), `tsb addr` (test and set bits)

**Stack-relative addressing (local variable access):**
  - 6502: requires manual stack pointer with `tsx` / `lda $0100,x`
  - 65C02: `lda $NN,s` / `sta $NN,s` (stack-relative addressing built-in)

**65816 compatibility note:** The Commander X16 can use a 65816 CPU which is
mostly 65C02 compatible but lacks the `rmb`, `smb`, `bbs`, `bbr`
instructions. The code generator should avoid these instructions when targeting
CX16 even if a 65C02 CPU is selected, or add a post-generation scan to reject
them.

A code generator should check `options.compTarget.cpu` at the point of
emitting these instructions and select the appropriate variant, rather than
relying on a post-processing pass.

## 13. Lessons from the Existing 6502 Code Generator

This section documents design decisions, pitfalls, and notable patterns from the
existing 6502 code generator that a new IR-based backend should consider.

### 13.1 Assignment Engine as Central Abstraction

The existing code generator models all data movement as `AsmAssignment`
objects connecting an `AsmAssignSource` to one or more `AsmAssignTarget`
instances. This decouples "what value to move" from "how to move it" and
provides a clean dispatch table for all (source, target) type combinations.
A new backend should adopt a similar model.

Source kinds: literal number, literal boolean, variable, array element,
memory address, CPU register, expression (complex).

Target kinds: variable, array element, memory address, CPU register, pointer
dereference, void (discard result).

### 13.2 Static Variable Allocation, No Stack Frames

All variables are statically allocated. There is no runtime stack frame for
local variables -- they live in BSS or zero-page and are initialized by
explicit assignments in the subroutine body. Parameters are also statically
allocated. This avoids stack depth issues on the 6502 (which has a 256-byte
hardware stack) but means recursion is not supported.

Dirty variables (`%option dirty`) are documented as undefined on subroutine
entry; the optimizer omits their re-initialization at subroutine entry. This is
worth preserving as an optimization hint.

### 13.3 Zero-Page Scratch Variables

A small set of fixed zero-page locations is reserved for temporary values
during expression evaluation:

| Name          | Size   | Purpose                           |
|---------------|--------|-----------------------------------|
| SCRATCH_B1    | 1 byte | General-purpose byte temp         |
| SCRATCH_REG   | 1 byte | Register save/restore temp        |
| SCRATCH_W1    | 2 words| General-purpose word temp (2)     |
| SCRATCH_PTR   | 2 bytes| Pointer for indirect access       |

These are defined in the header and used throughout the generated code.
A new backend should reserve equivalent scratch space and use it consistently.
Note that the byte-sized `SCRATCH_REG` aliases the low byte of a word scratch
location -- this is a known fragility to be handled carefully.

### 13.4 Zero-Page vs. Non-Zero-Page Code Paths

Many operations have two code paths depending on whether a variable is in
zero-page or not. For example, pointer indirection through a zero-page
variable uses `(zp),y` addressing directly, while a non-ZP pointer must
first be copied to a scratch pointer in zero-page. A new backend should track
this distinction and generate different code accordingly.

The `isZpVar()` check is used pervasively. In an IR-based backend, this
information should be available as a property of the variable's storage
allocation.

### 13.5 Floating-Point is Entirely Library-Driven

All floating-point operations call into target-specific ROM routines via
`jsr`. The compiler does not generate inline FP code. The floating-point
accumulator (FAC1) is the primary operand register, FAC2 is secondary.
Float-to-integer conversion, comparison, and arithmetic all go through these
library entry points.

Float constants are deduplicated globally across the entire program and
emitted as raw bytes in the target's float byte format (typically 5 bytes).

### 13.6 Self-Modifying Code for Non-ROMable Targets

When `romable=false` (the default), the code generator may use self-modifying
code patterns (e.g., writing comparison immediates into `cmp #xx` operands)
to avoid temporary variable overhead. This is incompatible with ROM-based
programs and must be disabled when `romable=true`. A new backend should
check the romable flag before emitting self-modifying patterns.

### 13.7 Assembly-Level Peephole Optimization is Necessary

The existing code generator runs a post-generation peephole optimizer on the
assembly text. Key optimizations to preserve in a new backend:

- **Store-load elimination:** `sta X / lda X` -> remove the redundant load
- **Load-load elimination:** `lda V / sta X / lda V` (same value) -> remove
- **Push-pop elimination:** `pha / pla` -> remove both
- **JSR+RTS to JMP:** `jsr Sub / rts` -> `jmp Sub`
- **Comparison with zero:** `lda X / cmp #0` -> remove `cmp` (flags already set)
- **Branch inversion:** `beq L / jmp A / L:` -> `bne A` (shorter)
- **Canceled increments:** `iny / dey` -> remove both

**Critical safety concern:** The optimizer must NOT eliminate loads from
I/O-mapped addresses (reading hardware registers has side effects). The
code generator should track which addresses are I/O and communicate this
to the optimizer, or accept that certain loads cannot be eliminated.

### 13.8 Symbol Prefixing is Done Before Code Generation

All user symbols are renamed with type prefixes (`p8b_`, `p8s_`, etc.)
by walking the AST and modifying node names *before* code generation. A new
`SymbolTable` is then constructed from the prefixed AST. This means the
symbol table used during code generation is NOT the same instance produced
by earlier compilation phases.

A new backend should either adopt the same approach or work with unprefixed
symbols and let the assembler output handle name mangling differently.
Key implications:
- Struct field names are **not** prefixed during the prefix pass (they are
  handled lazily at code generation time).
- After renaming, all `subType` references in the AST must be updated to
  point to entries in the new symbol table.
- The `SymbolTable`'s cached `flat` property becomes stale after renaming
  and must be rebuilt.

### 13.9 CPU Register Model Abstraction

The existing code uses a rich `RegisterOrPair` enum to model registers:

| Register       | Composition    | Notes                            |
|----------------|----------------|----------------------------------|
| A              | Single byte    | Accumulator                      |
| X              | Single byte    | Index register                   |
| Y              | Single byte    | Index register                   |
| AY             | Word           | A (lo), Y (hi)                   |
| AX             | Word           | A (lo), X (hi)                   |
| FAC1           | Float (5 byte) | FP accumulator                   |
| FAC2           | Float (5 byte) | FP secondary                     |
| R0-R15         | Byte/Word      | CX16 virtual registers (memory-mapped I/O) |
| R0R1-R14R15    | Long (4 byte)  | Paired virtual registers         |
| Status flags   | Bit            | Carry, Zero, Negative, Overflow  |

A new backend should define a similar abstract register set that maps onto
the target CPU's actual registers, with size and pairing information.

### 13.10 Fixed-Point Scratch Registers on CX16

On the Commander X16 target, there are 16 virtual registers (R0-R15) at fixed
memory-mapped I/O addresses. These are used as fast temporary storage for
intermediate values, multi-word operations, and multi-value returns.
R14/R15 are reserved for long return values; R15 alone is used for secondary
word return values. A new backend should define which (if any) target-specific
fast registers are available and how they are allocated.

### 13.11 Struct Instance Initialization Quirk

Zero-page string and array variables with compile-time initial values require
special handling: their init data is stored in a ROM block (labeled with a
`_init_value` suffix), and a runtime `memcopy` copies the data from ROM
into the zero-page locations at startup. This is necessary because zero-page
locations cannot have static initializers in most binary formats.

A new backend should replicate this pattern for any variables in a "fast"
memory region that lacks static initialization support.

### 13.12 Float Eval Result Variables

Per-subroutine temporary float variables (`subroutineFloatEvalResultVar1`
and `subroutineFloatEvalResultVar2`, 5 bytes each) are allocated in
BSS_NOCLEAR for intermediate float expression results. A new backend should
provide equivalent scratch space for float temporaries, sized according to
the target's float byte format.

The `dirty` attribute is set on these temporaries because they don't need
initialization.

### 13.13 Pitfall: `TODO` and `AssemblyError` for Unimplemented Paths

The existing code generator uses `throw AssemblyError(...)` and `TODO()`
for type combinations or edge cases that are not yet implemented. This is a
placeholder for incomplete features. A new backend should either implement
these paths cleanly or define a clear error-reporting strategy for unsupported
operations.

Several of these TODOs have been fixed in the IR codegen's builtin function
handling (`BuiltinFuncGen.kt`): `rol`, `ror`, `rol2`, `ror2`, `setlsb`, and
`setmsb` on pointer-deref arrays now fall through to address-computation
paths instead of throwing. The corresponding TODOs remain in the 6502 codegen
(`BuiltinFunctionsAsmGen.kt`).

### 13.14 Source Line References in Assembly Output

The generated assembly should include comments referencing the original
`.p8` source file and line number for each generated block of instructions.
This is controlled by the `includeSourcelines` compilation option.

In the old code generator (AST-based), each AST node carries a `Position`
object with file, line, and column information. The code generator tracks
the last emitted source line and inserts `; source: filename.p8:NN`
comments whenever the line changes.

For an IR-based code generator, source positions are stored at the
`IRCodeChunk` level via `sourceLinesPositions: List<Position>`
(rather than per-instruction). The code generator should emit a source
comment at the beginning of each chunk (or at subroutine entry) using
the first non-dummy position from this list. A `lastSourceLine` tracker
avoids duplicate consecutive comments.

The comment format should be:

```
; source: filename.p8:42
```

### 13.15 Output Type and Launcher Handling

The code generator must handle multiple output formats:
- `PRG` with `BASIC` launcher: emits a BASIC SYS stub with a
  `prog8_entrypoint` label, `.word` / `.null` directives
- `PRG` with `NONE` launcher: raw header at load address
- `RAW`: raw binary at load address
- `XEX`: Atari XEX format header
- `LIBRARY`: minimal `jmp main.start` header

All formats emit `prog8_program_start` at the entry point and share a
common startup sequence: `cld`, stack save via `tsx`/`stx prog8_lib.orig_stackpointer`,
system initialization calls, BSS clearing, and jump to `main.start`.
After `main.start` returns, execution jumps to `cleanup_at_exit`
which calls `sys.poweroff_system`.

The `includeSourcelines` option and user-supplied symbol definitions
(`options.symbolDefs` / `-esa`) are also emitted in the header.

### 13.16 The `prog8_program_end` Label

The code generator must emit a `prog8_program_end` label at the very end
of the generated output (after all code, data, and BSS sections). This label
marks where the program ends in memory and is used by:

- The `progend()` library routine
- Memory layout calculations (`MEMORY TOP`)
- Determining available space between program end and hardware boundaries

The old codegen emits this label at the end of its `footer()` method,
positioned after all sections. A new code generator should place it as the
last thing before assembly symbols and final output.

### 13.17 BSS_NOCLEAR and BSS Section Organization

Variables in BSS are split into two categories:
- **Clean** (`dirty == false`): cleared to zero at program startup.
  These go into the `BSS` section.
- **Dirty** (`dirty == true`): cleared at program startup but NOT at each
  subroutine entry. These go into `BSS_NOCLEAR`. The runtime cost of
  zeroing on every call is avoided while still having deterministic initial
  state at program start.

The virtual register file (`p8_regfile`) always lives in `BSS` (cleared).
Memory slabs go into a separate `BSS_SLABS` sub-section to allow selective
relocation (e.g., to golden RAM or a high memory bank).

A `PROG8_VARSHIGH_RAMBANK` equate documents which RAM bank is active for
relocated BSS sections. When relocation is active, non-relocatable sections
(`BSS_NOCLEAR`, `BSS_SLABS`) are emitted before the `* =` directive
that switches to the relocated area.
