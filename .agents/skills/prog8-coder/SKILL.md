---
name: prog8-coder
description: Writing or understanding Prog8 programs and Prog8 IR
---

# Prog8 Coder

You are an expert Prog8 assistant. Keep answers concise and practical. This
skill covers `.p8` source and `.p8ir` intermediate representation for the
6502, M68000, and virtual targets.

Various reference documentation is available locally in
`~/Documents/prog8-dev-resources`: 

Prog8 reference documentation, emulator manuals such as VICE and the X16
emulator, the 64tass/vasm/vlink assembler and linker docs, X16 hardware and register specs,
the Amiga NDK (docs and includes), and other resources. 
Search in this folder first before looking online!


## Essentials

- A program normally has a `main` block with a parameterless `start` entry
  subroutine. Other blocks and subroutines may be added.
- `%import modulename` imports a module without aliases. Use its defined prefix,
  such as `%import textio` followed by `txt.print(...)`.
- `sys` is always available and needs no import.
- `.p8ir` is target-independent IR and can be run with
  `prog8c -vm program.p8ir`.
- Use `%zeropage basicsafe` in test programs when a clean return is needed.
- Find library routines with `prog8c -libsearch <regex>` or extract them with
  `prog8c -libdump <dir>`.
- Options accept single-dash or double-dash (`-target` == `--target`). Use
  `-out <dir>` to place output in a directory, or `-o <file>` to name the exact
  final program artifact (all other output files then land beside it).

For CX16 emulator programs, use `%encoding iso`, call `txt.iso()` in `start`,
and finish with `sys.poweroff_system()`. CBM targets use PETSCII by default;
avoid uppercase in test output unless intentional graphics. The virtual target
uses ISO when configured with `%encoding iso` and `txt.iso()`.

Do not use `%option no_sysinit` on `amiga500`, `amiga1200`, or `qemu68k` targets when the goal
is to create normal runnable programs. It skips critical startup logic including
library initialization (DOS, graphics, intuition, timer) and CLI argument
handling, causing programs to crash on start. It is appropriate for library
modules, IRQ handlers, boot stubs, or code that deliberately avoids those
libraries.

## Types and Memory

- Primitive types include `bool`, `byte`, `ubyte`, `word`, `uword`, `long`,
  `float`, `str`, and `pointer`.
- `pointer` is target-sized: 16-bit on 6502 targets, 32-bit on `amiga500`,
  `qemu68k`, and `virtual`. Use it for portable addresses, not `uword` or
  `long` chosen by assumption.
- M68000 targets are big-endian and use 32-bit pointers. Load the `m68k-coder`
  skill for assembly details.
- `float` requires `%import floats`.
- On 6502 and virtual targets, variables are statically allocated and
  zero-initialized. There is no normal call stack for locals, so recursion
  overwrites locals unless an explicit hardware or software stack is used.
  Iterative rewrites are preferred. Arrays are cleared once at startup here,
  not on every subroutine entry.
- On the M68000 targets (`amiga500`, `amiga1200`, `qemu68k`) ordinary subroutine
  locals and parameters live in an activation record on the CPU stack, so true
  recursion and re-entrancy work. `link a5,#-N` / `unlk a5`, frame pointer `A5`,
  locals at negative offsets, parameters at positive offsets. Block-level
  variables, globals, `@shared`, memory slabs, and objects needing a permanent
  external address stay static. Clean locals are zeroed on every call
  including arrays, so the compiler warns for framed arrays over 8 bytes; use
  `@dirty` when you assign first, otherwise you read stack garbage.
- Normal M68K calls use uniform 4-byte, right-justified argument slots. The caller
  pushes arguments left-to-right and removes the complete argument area after the
  call. Framed parameters start at positive `A5` offsets, with the first parameter
  at `8 + 4 * (parameter_count - 1)`; locals use negative offsets. See
  `docs/source/technical.rst` for the full convention and static fallback rules.
- `memory(name, size)` reserves static memory and returns a `pointer`.
- `str` and arrays have compile-time byte budgets. On 6502 targets the usual
  limit is 256 bytes; M68000 targets allow up to 32768 bytes. Use `memory()`
  for larger data.
- Word arrays are split into LSB/MSB arrays by default. Use `@nosplit` when
  contiguous storage is required.
- Variable tags follow the type and array dimensions, before the name:

```prog8
ubyte[8] @shared vera_storage
uword @requirezp address
```

Use `@shared` for values accessed externally by assembly. Use `@zp` and
`@requirezp` sparingly because zeropage is limited. Never assume virtual
registers survive calls. Long operations may clobber `R12-R15`.

## Syntax Pitfalls

- Hex uses `$FF`, binary uses `%1010`, and casts use `expression as type`.
- There is no automatic type widening: `byte * byte` remains a byte. Cast
  explicitly when a wider result is needed.
- `&` is an untyped address and `&&` is a typed pointer. Complex pointer field
  assignments may require `ptr^^.field`.
- `and`, `or`, `xor`, and `not` are logical operators. Use `&`, `|`, `^`, and
  `~` for bitwise operations.
- `and` and `or` short-circuit, so the right operand may not be evaluated.
- There is no `elif`, bare block, or semicolon statement separator. Semicolon
  starts a comment. Prefer one statement per line and four-space indentation.
- `defer` executes registered statements in reverse registration order.
- Array indexing starts at zero. Use `len(array)` rather than hardcoded sizes.
- There is no function overloading. Call type-specific library routines such as
  `txt.print_ub` or `txt.print_w`.

## Control Flow

Prog8 supports `if`/`else`, `when`, `for`, `while`, `do`/`until`, `repeat`,
`unroll`, `break`, `continue`, `goto`, and labels. `unroll` duplicates a
constant-count body at compile time and does not support `break` or `continue`.
`repeat` is usually more efficient than `for` when no loop variable is needed.
Use `if_cs`, `if_cc`, `if_z`, and `if_nz` for direct CPU-flag branches.

## Subroutines and Assembly

- Subroutines can return zero, one, or multiple values:
  `a, b = routine()` or `void routine()`.
- On M68K, ordinary calls preserve the caller's active frame. A `goto` or branch
  that leaves the current subroutine must tear that frame down before transferring
  control. The built-in `call(address)` is a zero-argument indirect call; it does
  not marshal stack arguments.
- Avoid `private` unless requested. Prog8 symbols are public by default.
- `asmsub` bodies contain only one `%asm {{ ... }}` node. Parameters are type
  checked and documented, but assembly must use the mapped registers. Declare
  every modified hardware register in `clobbers`.
- On M68K targets never clobber `A5`, the stack frame pointer. On the Amiga
  targets never clobber `A6` either, it holds the AmigaOS library base.
- `extsub` maps a signature to a fixed external address and has no body. Use
  it for ROM, kernel, drivers, or binary-library routines.
- For inline assembly, load `asm6502-coder` for 64tass syntax, target-specific
  instructions, zero-page rules, and symbol references. Load `m68k-coder` for
  M68000 assembly.

## Interrupts and Hardware

- Keep IRQ handlers short. Set a flag and do substantial work in the main loop.
- Virtual registers `R0-R15` are not preserved across IRQ handlers. Avoid them
  or save and restore them with the library routines.
- CX16 IRQ handlers that touch VERA registers must save and restore VERA
  context.
- Use only symbolic scratch names such as `P8ZP_SCRATCH_W1` and
  `cx16.r0`; never hardcode zeropage addresses.

## Verification

- Use the `virtual` target for behavioral tests when possible:
  `prog8c -target virtual -emu program.p8`.
- Use `-check` for syntax and semantic checks without output generation.
- Use `-noopt` to determine whether a failure is optimizer-related.
- For IR execution, use `-vmtrace` when control flow needs inspection.
