---
name: prog8-coder
description: Write Prog8 programs (6502-targeted language with 8-bit retro systems as targets)
license: MIT
compatibility: opencode
---

# Prog8 Coder Skill

You are working with **Prog8** source code (`.p8` files) or its Intermediate Representation (`.p8ir` files). Prog8 targets 8-bit systems (C64, CX16, C128, PET32) with the 6502 CPU, plus a `virtual` target for testing.

Follow ALL the rules below carefully.

## General & Setup
- A program = a `main` block containing a `start` subroutine entry point, plus optional other subroutines/blocks
- Add `%zeropage basicsafe` at the top of your program to allow clean return on exit (instead of resetting the machine/emulator)
- Module imports: `%import modulename` — no `as` aliasing. Use the module's defined prefix (e.g., `%import textio` → `txt.xxx`)
- **Virtual target for testing**: Use `prog8c -target virtual -emu input.p8` or `prog8c -vm input.p8ir` for fast testing
- **Fast syntax check**: `prog8c -check input.p8` — does NOT produce output files (.prg, .asm, etc.), just errors
- **Output directory**: `prog8c -out outdir input.p8` (default: same dir as source)
- **Compilation outputs**: `*.prg` (program binary), `*.asm` (assembly listing), `*.list` (full listing), `*.p8ir` (IR for VM), `*.vice-mon-list` (Vice debug symbols)
  - `.p8ir` files contain the **Intermediate Representation** — a sequence of named chunks, each containing typed instructions and virtual registers. This is a target-independent representation of the program, executable by the built-in Virtual Machine via `prog8c -vm file.p8ir`. Useful for debugging the compiler's code generation path without involving 6502 assembly.
- **Debugging switches**: `-noopt` (disable optimizations), `-printast1` (parsed AST), `-printast2` (optimized simple AST), `-compareir` (compare IR outputs)
- **TODO list**: Check `docs/source/todo.rst` to see what features are NOT yet implemented
- **Test programs**: add `%zeropage basicsafe` and `%option no_sysinit` at top
- **`sys` module**: always available, no import needed
- **CX16 debugging**: Add `%encoding iso`, call `txt.iso()` in `start()`, end with `sys.poweroff_system()`. For emulator: `x16emu -echo iso -run -prg input.prg 2>&1 | grep ...`

## Datatypes & Variables
- Primitives: `bool`, `byte`, `ubyte`, `word`, `uword`, `long`, `float`, `str`
- `ubyte`/`uword` = unsigned; `long` = signed 4-byte; `float` = 5-byte MS format; `str` = 0-terminated ubytes (max 255 chars)
- **float requires `%import floats`** at top of file, else compiler errors
- Arrays: max 256 bytes (512 for split word arrays). For larger data, use `memory()` + pointers
- `memory(name, size)` returns a `uword` address to a statically reserved block of memory
- Struct initialization: `^^StructType ptr = ^^StructType:[val1,val2,...]` (the `^^StructType:` can be omitted if inferable)
- Struct fields: only simple types + `str` allowed. NO arrays as fields. `str` in struct = `^^ubyte`
- Word/pointer arrays split into LSB/MSB by default. Override with `@nosplit`
- **No call stack**: all variables statically allocated. No recursion without manual stack management
- Variables zero-initialized (globals at start, locals on subroutine entry)
- `@shared` marks variables as "used by external code" (assembly), prevents optimization
- `@zp`/`@requirezp`: use sparingly — only for pointers (limited zeropage space)
- Pointer-like typed pointers (`^^type`) support C-style scaled arithmetic; `uword` pointers always treat element as 1 byte
- `&` = untyped address (uword); `&&` = typed pointer
- Available zeropage scratch: `P8ZP_SCRATCH_B1`, `P8ZP_SCRATCH_REG`, `P8ZP_SCRATCH_W1`, `P8ZP_SCRATCH_W2`, `P8ZP_SCRATCH_PTR` — and cx16 virtual registers R0-R15 on all targets
- Virtual registers (`cx16.r0`–`cx16.r15`): global 16-bit, NOT preserved across calls. R12-R15 may be clobbered by long ops. Save/restore in IRQs with `cx16.save_virtual_registers()`/`cx16.restore_virtual_registers()`
- Math performance: integer trig (`math.sin8`, `math.cos8`) uses fast LUTs; float trig (`floats.sin`/`cos`) is much slower

## Recursion & Stack Management
No call stack for variable storage — recursion overwrites locals. To handle it:
1. **CPU hardware stack**: `push()`/`pushw()`/`pushl()`/`pushf()` and `pop()`/`popw()`/`popl()`/`popf()`. Save/restore locals around recursive calls
2. **Software stacks**: `buffers.stack` (uword) / `buffers.smallstack` (ubyte). Both provide `push_b()`/`push_w()` and `pop_b()`/`pop_w()`
3. **Iterative rewrite (preferred)**: Many recursive algorithms work as `repeat` loops with explicit bounds — avoids all stack overhead

## Strings, Arrays & Pointers
- `str` / array: max 256 bytes. `long[]` limited to 64 entries (64x4=256). `str[]` for string arrays: `str[5] names = ["a","b","c","d","e"]`
- 2D arrays: `type[rows][cols] name`, access `name[r][c]`. Flat init list only (no nested `[[...]]`). Total size still ≤ 256 bytes
- str/array passed as pointer to subroutine (receiving subroutine gets `^^ubyte` or `^^element`)
- **No const pointers** or pointer-to-pointer currently supported
- **Parsing limitation**: `pointer[index].field` as assignment target needs `^^`: `pointer[index]^^.field = value`
- **Pre-allocate buffers**: `str buffer = "." * 50` (empty `""` allocates nothing — `strings.append()` will fail)
- **No reassignment**: can't `buffer = "new text"` after declaration. Use `strings.copy()`/`strings.ncopy()`
- String concat is expensive on 6502. Prefer separate prints over concatenation
- **Efficient buffer iteration**: prefer `ptr++` + `@(ptr)` over `@(buffer + offset)`. Exception: if offset is a `ubyte` (≤ 255), `buffer[offset]` works fine
- `@(ptr)` = peek/poke byte. For words: `peekw`/`pokew`, longs: `peekl`/`pokel`, floats: `peekf`/`pokef`, bools: `peekbool`/`pokebool`
- Use `len(array)` instead of hardcoded sizes
- Array indexing is 0-based: `arr[0]` is first element
- Static memory only — real dynamic allocation impossible, but can emulate with a simple arena allocator over a `memory()` slab

## Logic & Control Flow
- Logical operators (short-circuit, bool only): `and`, `or`, `xor`, `not`
  - In `a and b`: if `a` is false, `b` is NOT evaluated
  - In `a or b`: if `a` is true, `b` is NOT evaluated
  - Important when `b` has side effects
- Bitwise operators: `&`, `|`, `^`, `~`, `<<`, `>>`
- Bit rotation: `rol()`/`ror()` (through carry), `rol2()`/`ror2()` (no carry)
- CPU status flag branches: `if_cs`, `if_cc`, `if_z`, `if_nz` (compile to single 6502 branch instructions)
- Use `when` with choice blocks instead of multiple `if`
- If-expressions for simple value assignments based on a choice
- **Optional braces in if/else**: when the `if` or `else` body is a single statement, the `{ }` can be omitted. Place the statement on the next line, indented. Example:
  ```
  if x < 5
      txt.print("small")
  ```
- `defer` defers statement execution until scope exit
- `goto`, labels, jump lists allowed
- **Common mistake**: `and`/`or` for bitmasking — use `&`/`|` instead!

## Loop Constructs
Prog8 supports these loop types. All support `break` and `continue` (except `unroll`).

### `for` loop — iterate over a range or array
- Loop variable **must be declared separately** before the `for` statement
- Works with `ubyte`, `byte`, `uword`, `word`, `long`, pointer types (NOT `float`)
- Iterates over ranges (`start to end`), descending ranges (`start downto end`), or arrays/strings
- Optional `step <constant>` for non-unit step sizes
- Loop variable value **after the loop is undefined** — don't rely on it
- Descending loops with `downto` usually produce more efficient 6502 code
```
ubyte i
for i in 20 to 155 {
    ; body
    break       ; exit loop
    continue    ; next iteration
}

; descending
for i in 155 downto 20 {}
for i in 155 to 20 step -1 {}

; iterate over array elements
uword[] fib = [0, 1, 1, 2, 3, 5, 8, 13]
uword num
for num in fib {
    ...
}
```

### `while` loop — repeat while condition is true
```
while condition {
    ; body
    break
    continue
}
```

### `do`-`until` loop — always executes body at least once
```
do {
    ; body
    break
    continue
} until condition
```

### `repeat` loop — repeat a fixed number of times (most efficient)
- Most efficient code generation — prefer over `for` when loop variable not needed
- Omit count for infinite loop (still supports `break`)
```
repeat 15 {
    ; body
    break
    continue
}

; infinite:
repeat {
    ; body
    break if x==5
}
```

### `unroll` loop — compile-time code duplication
- Not a real loop — duplicates body N times at compile time
- No `break`/`continue` allowed
- Only simple statements (assignments, calls) in body
- Constant iteration count required
```
unroll 80 {
    cx16.VERA_DATA0 = 255
}
```

## Subroutines & Return Values
- Symbols public by default; use `private` to restrict to block
- `inline` keyword for subroutines to suggest inlining
- No function overloading (except builtins). Cannot use builtin names (msw, lsw, msb, lsb, mkword, mklong, peek, peekw, peekl, etc.) as variable/sub names
- Can return 0, 1, or multiple values: `a, b, c = routine()`. Use `void` to skip: `void routine()`, `a, void, c = routine()`
- Nested subroutines access parent scope variables directly

## Assembly Subroutines (asmsub)
For kernel (ROM) routines or low-level assembly that gets arguments via registers.
- Parameter passing via registers: `@A` (accu), `@X`, `@Y`, `@AX`/`@AY` (16-bit), `@R0`-`@R15` (virtual regs), `@FAC1`/`@FAC2` (float), `@Pc` (carry), `@Pz` (zero)
- Return value: `-> type @register` (e.g., `-> ubyte @A`, `-> bool @Pz` for immediate branch use)
- Clobbers: `clobbers (A, X, Y)` — list all modified registers
- Parameter names are documentation only — use registers in assembly, not parameter names
- Create symbolic aliases at assembly start: `x1 = cx16.r0`, `x1_lo = cx16.r0L`
- Example:
  ```prog8
  asmsub line(uword x1 @R0, ubyte y1 @A, uword x2 @R1, ubyte y2 @Y) clobbers (A, X, Y) {
      %asm {{
          x1 = cx16.r0
          x2 = cx16.r1
          lda  x1
      }}
  }
  ```
- Symbol prefixes in assembly: `p8v_` (variables), `p8s_` (subroutines), `p8b_` (blocks), `p8c_` (constants), `p8l_` (labels), `p8t_` (structs), `p8_` (other)
- Fully qualified: `p8b_blockname.p8v_varname`, `p8b_blockname.p8s_subname.p8v_localvar`
- Within a `.proc`, short names often work. `%option no_symbol_prefixing` disables prefixes (used by `cbm`, `cx16`, `txt`)
- Split word arrays: append `_lsb` / `_msb` to variable name (e.g., `p8v_myarray_lsb`)
- CX16 target only: use 65C02 instructions (STZ etc). Others: 6502 only
- Assembly syntax: 64tass assembler. `.proc`/`.pend`, `_label` for locals, `.byte`/`.word`/`.dword` for data, `=` for equates
- Instructions like `rol`, `ror`, `asl`, `lsr` require explicit operand: `rol a`, not just `rol`
- Anonymous labels: `+` (forward), `-` (backward), branch with `+`, `++`, `+++` or `-`, `--`, `---`

## Standard Library
- Find routines, functions, variables, modules and signatures in the symbol dump file for the given compilation target. 
  - Online location: https://prog8.readthedocs.io/en/latest/libraries.html#low-fi-variable-and-subroutine-definitions-in-all-available-library-modules  they are linked there 1 for each compilation target
  - Structure: builtin functions, then module sections with variables/constants (`type name`) and subroutines (`name (params) -> returntype`)
- Text output: `textio` module (`txt.print`, `txt.chrout`, `txt.print_b`/`_ub`/`_w`/`_uw`/`_l`/`_bool`, `txt.print_f` for floats, `txt.spc()`, `txt.nl()`)
- Math: `math` module — integer trig (`sin8`, `cos8`) via fast LUTs; `math.rnd()` for random numbers
- String conversion: `conv` module (`str_uword`, `str2word`, etc.) — for printing numbers use txt routines instead
- Char operations: `strings` module (`isdigit`, `isxdigit`, `isupper`, `islower`, `isletter`, `isspace`, `isprint`) — use these instead of manual ASCII/PETSCII arithmetic
- String functions return useful lengths — capture them: `len = strings.copy(dest, src)`, `len = strings.append(buf, text)`, `len = strings.upper(mystr)`

## Syntax & Formatting
- Hex: `$FF` (not `0xFF`); Binary: `%1010` (not `0b1010`). Underscores for readability: `25_000_000`
- 4-digit hex `$0000` = uword. No type suffixes (no `0L`). Cast: `expr as type`
- Augmented assignment: `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, `>>=`
- **`;` starts a comment to end of line** — NOT a statement separator. One statement per line
- **No `elif`**: use nested `else { if ... }`
- Type casting: `expression as type` (e.g., `bytevar as word`). `as` has very low precedence (lower than arithmetic)
- **No automatic type widening**: `byte*byte=byte` (overflow possible!). Cast explicitly
- **No block scope**: `for`/`if` don't introduce scope. Only subroutines do. Variables in blocks are hoisted to subroutine level
- **No bare `{ }` blocks** like C/Java
- Indentation: 4 spaces for .p8 and .asm files (no tabs)
- Character encoding: 6502 targets use PETSCII by default (call `txt.lowercase()` at start for lowercase). Virtual target uses ISO (`%encoding iso` + `txt.iso()`)
- Array size inferred from initializer: `str[] types = ["a", "b", "c"]`
- Enums: `Enum::Value` syntax (double colon), declared inside a block. Use enums for related values, `const` for standalone
- Avoid `globals.XXXX` — move constants closer to where they're used
- Member access through pointers: use `.` for both direct and pointer access. The compiler infers the type. For complex assignment targets, `^^` may be needed: `ptr^^.field = value`
- Qualified names: must use full path from top level (e.g., `cx16.r0`, not relative)
- **No block scope** — variables in `if`/`for`/`repeat` blocks are hoisted to subroutine level
- **No reassigning strings** after declaration — use `strings.copy()`/`strings.ncopy()`
- **Pre-allocate string buffers** — `""` allocates nothing
