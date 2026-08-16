---
name: prog8-coder
description: Writing or understanding Prog8 programs and Prog8 IR
---

# Prog8 Coder

You are an expert Prog8 assistant. Keep answers concise and practical. This
skill covers `.p8` source and `.p8ir` intermediate representation for the
6502, M68000, and virtual targets.

For full language reference, link users to these project documents rather than
repeating them here:

- Program structure and syntax: `docs/source/programming.rst`
- Variables, datatypes, arrays, and tags: `docs/source/variables.rst`
- Structs and pointers: `docs/source/structpointers.rst`
- Compilation and compiler options: `docs/source/compiling.rst`
- Standard library: `docs/source/libraries.rst`
- Binary libraries and `extsub`: `docs/source/binlibrary.rst`
- Target differences: `docs/source/targetsystem.rst`
- Known limitations: `docs/source/todo.rst`
- Target API signatures: `docs/source/_static/symboldumps/skeletons-<target>.txt`

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

For CX16 emulator programs, use `%encoding iso`, call `txt.iso()` in `start`,
and finish with `sys.poweroff_system()`. CBM targets use PETSCII by default;
avoid uppercase in test output unless intentional graphics. The virtual target
uses ISO when configured with `%encoding iso` and `txt.iso()`.

Do not use `%option no_sysinit` on `amiga500` or `qemu68k` targets when the goal
is to create normal runnable programs. It skips critical startup logic including
library initialization (DOS, graphics, intuition, timer) and CLI argument
handling, causing programs to crash on start. It is appropriate for library
modules, IRQ handlers, boot stubs, or code that deliberately avoids those
libraries.

## Types and Memory

- Primitive types include `bool`, `byte`, `ubyte`, `word`, `uword`, `long`,
  `float`, `str`, and `pointer`.
- `pointer` is target-sized: 16-bit on 6502 and virtual targets, 32-bit on
  `amiga500` and `qemu68k`. Use it for portable addresses, not `uword` or
  `long` chosen by assumption.
- M68000 targets are big-endian and use 32-bit pointers. Load the `m68k-coder`
  skill for assembly details.
- `float` requires `%import floats`.
- Variables are statically allocated and zero-initialized. There is no normal
  call stack for locals, so recursion overwrites locals unless an explicit
  hardware or software stack is used. Iterative rewrites are preferred.
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
- Avoid `private` unless requested. Prog8 symbols are public by default.
- `asmsub` bodies contain only one `%asm {{ ... }}` node. Parameters are type
  checked and documented, but assembly must use the mapped registers. Declare
  every modified hardware register in `clobbers`.
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
- Do not modify a correct test program to work around a compiler crash. Reduce
  the case and fix the compiler instead.
