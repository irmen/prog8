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
- **Git operations**: When moving, renaming, or deleting git-tracked files, **always use `git mv` or `git rm`** instead of plain `mv`/`rm`. This preserves history and properly stages the change. Plain `mv`/`rm` causes git to see them as delete+add (losing history).
- A program = a `main` block containing a `start` subroutine entry point, plus optional other subroutines/blocks
- Add `%zeropage basicsafe` at the top of your program to allow clean return on exit (instead of resetting the machine/emulator)
- Module imports: `%import modulename` — no `as` aliasing. Use the module's defined prefix (e.g., `%import textio` → `txt.xxx`)
- **Virtual target for testing**: Use `prog8c -target virtual -emu input.p8` or `prog8c -vm input.p8ir` for fast testing
- **Fast syntax check**: `prog8c -check input.p8` — does NOT produce output files (.prg, .asm, etc.), just errors
- **Output directory**: `prog8c -out outdir input.p8` (default: same dir as source)
- **Compilation outputs**: `*.prg` (program binary), `*.asm` (assembly listing), `*.list` (full listing), `*.p8ir` (IR for VM), `*.vice-mon-list` (Vice debug symbols)
  - `.p8ir` files contain the **Intermediate Representation** — a sequence of named chunks, each containing typed instructions and virtual registers. This is a target-independent representation of the program, executable by the built-in Virtual Machine via `prog8c -vm file.p8ir`. Useful for debugging the compiler's code generation path without involving 6502 assembly.
- **Debugging switches**: `-noopt` (disable optimizations), `-printast1` (parsed AST), `-printast2` (optimized simple AST), `-compareir` (compare IR outputs), `-dumpsymbols` (print all symbols), `-dumpvars` (print all variables)
- **Library search (preferred)**: `prog8c -libsearch <regex>` — search for a regex pattern in the embedded library files. Extremely useful to quickly find library routines, variables, strings, or signatures (e.g., `prog8c -libsearch "txt\."` lists all textio routines; `prog8c -libsearch "sin"` finds math functions)
- **Library dump**: `prog8c -libdump <dir>` — extract all embedded library source files into a directory for direct inspection (less common, use `-libsearch` first)
- **Other useful flags**: `-quiet` (suppress messages), `-warnimplicitcasts` (warn on implicit type widening), `-daemon` (keep a background compiler process alive to speed up multiple successive compilations — must be passed on every `prog8c` invocation)
- **Test programs**: add `%zeropage basicsafe` and `%option no_sysinit` at top
- **Compiler unit test snippets**: These are **prog8 code snippets embedded in the Prog8 compiler's own Kotlin unit tests** (e.g., in `TestAstChecks.kt`, `TestOptimization.kt`, etc.). They are NOT standalone Prog8 programs:
  - Keep them self-contained — avoid `%import` directives so they don't depend on library files (the test setup may not have the library search path configured)
  - Use the `cx16` target (not `virtual`, unless specifically testing the VM)
  - Consider setting `writeAssembly=false` if the test only needs to check the generated AST (much faster)
  - Use `optimize=false` by default for these snippets
  - Do NOT use the `%encoding iso` / `txt.iso()` / `sys.poweroff_system()` pattern in these snippets — that's only for real CX16 emulator runs
- **`sys` module**: always available, no import needed
- **CX16 debugging**: Add `%encoding iso`, call `txt.iso()` in `start()`, end with `sys.poweroff_system()`. For emulator: `x16emu -echo iso -run -prg input.prg 2>&1 | grep ...`

## Datatypes & Variables
- Primitives: `bool`, `byte`, `ubyte`, `word`, `uword`, `long`, `float`, `str`
- `ubyte`/`uword` = unsigned; `long` = signed 4-byte; `float` = 5-byte MS format; `str` = 0-terminated ubytes (max 255 chars)
- **float requires `%import floats`** at top of file, else compiler errors
- Arrays: max 256 bytes (512 for split word arrays). For larger data, use `memory()` + pointers
- `memory(name, size)` returns a `uword` address to a statically reserved block of memory
- To point a typed pointer at a `memory()` block, assign the uword directly: `^^MyStruct ptr = memory("name", size)`. The `^^Type:expression` syntax (below) only works with array literals, not general uword expressions.
- Struct initialization: `^^StructType ptr = ^^StructType:[val1,val2,...]` (the `^^StructType:` can be omitted if inferable). This `^^Type:[...]` syntax does NOT work with variables or `memory()` — only literal arrays.
- Struct definitions must be inside a block, not at file level.
- Struct fields: only simple types + `str` allowed. NO arrays as fields. `str` in struct = `^^ubyte`
- Word/pointer arrays split into LSB/MSB by default. Override with `@nosplit`
- **No call stack**: all variables statically allocated. No recursion without manual stack management
- Variables zero-initialized (globals at start, locals on subroutine entry)
- `@shared`, `@zp`, `@requirezp`, `@dirty`, `@nosplit` are **tags** that go on variable declarations. **Place them after the datatype (and array specifiers), before the variable name(s):**
  ```
  private ubyte[8] @shared vera_storage     ; single var with tag
  ubyte @zp @shared varname                 ; multiple tags allowed
  ubyte @requirezp var1                     ; require zeropage
  ubyte[] @shared names                     ; array with tag
  ```
  The grammar is: `[private] datatype [arraydims] [tags...] identifierlist`
- `@shared` marks variables as "used by external code" (assembly), prevents the optimizer from removing them
- `@zp`/`@requirezp`: use sparingly — only for pointers (limited zeropage space)
- Pointer-like typed pointers (`^^type`) support C-style scaled arithmetic; `uword` pointers always treat element as 1 byte
- `&` = untyped address (uword); `&&` = typed pointer
- Available zeropage scratch: `P8ZP_SCRATCH_B1`, `P8ZP_SCRATCH_REG`, `P8ZP_SCRATCH_W1`, `P8ZP_SCRATCH_W2`, `P8ZP_SCRATCH_PTR` — and cx16 virtual registers R0-R15 on all targets
- Virtual registers (`cx16.r0`–`cx16.r15`): global 16-bit, NOT preserved across calls.
- **WARNING: Virtual registers in ISR/IRQ handlers**: The virtual registers R0-R15 are *not preserved* across the IRQ handler call. If your handler uses them, it will corrupt the interrupted program's state. Either avoid using them in the handler, or save/restore with `cx16.save_virtual_registers()` / `cx16.restore_virtual_registers()`. This applies to all targets, not just CX16.
- **WARNING: Long operations clobber R12-R15**: Some operations on `long` values use R12-R15 as temporary storage and will silently overwrite them. Do not rely on R12-R15 values when working with longs, and avoid using R12-R15 explicitly if your code uses long arithmetic.
- **WARNING: VERA registers in ISR handlers (CX16)**: If your IRQ handler reads or writes VERA control registers (e.g., `cx16.VERA_DATA0`, `cx16.VERA_ADDR_L`, etc.), you must save and restore the VERA context around the handler's work using `cx16.save_vera_context()` / `cx16.restore_vera_context()`. Without this, the handler will corrupt any VERA operations (tilemap updates, sprite positioning, etc.) happening in the interrupted main program.
- **IRQ handler best practices**: Keep handlers extremely short and fast — they run with interrupts disabled and steal cycles from the main program. Do NOT do lengthy processing, I/O, or complex subroutine calls inside the handler. Instead, set a boolean flag or semaphore that the main loop checks periodically, and do the actual work there.
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
- Assigning a uword address to a `str` field of a struct is done by direct assignment. Compute the address in a uword variable first, then assign: `cx16.r0 = &namebufs + offset; entry.name = cx16.r0`. The `^^ubyte:(expr)` cast syntax does not parse — use a temp uword instead.
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
- If-expressions for simple value assignments based on a choice
- **Optional braces in if/else**: when the `if` or `else` body is a single statement, the `{ }` can be omitted. Place the statement on the next line, indented. Example:
  ```
  if x < 5
      txt.print("small")
  ```
- `defer` defers statement execution until scope exit. Multiple defers fire in **reverse registration order** (LIFO / stack order — last deferred runs first). A defer is only registered if execution reaches that statement — conditional paths that skip the `defer` line will not register it.
- `goto`, labels, jump lists allowed
- **Common mistake**: `and`/`or` for bitmasking — use `&`/`|` instead!

### The when Statement
The `when` statement is a control flow construct that enables you to execute a specific action based on the value of an expression. It is generally more readable and often more efficient than a sequence of `if-else if` statements, as the compiler can optimize it into more efficient branching structures, such as a jump table.
- **Expression**: Evaluates an expression and compares it against case values.
- **Cases**: Defined by a value followed by the `->` operator.
- **Blocks**: Use `{ }` to enclose multiple statements for a case.
- **Else Clause**: Serves as a default handler; mandatory unless the expression type is fully covered.
- **Efficiency**: Recommended for handling sets of fixed choices as it typically results in better assembly code.

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
- **Don't use `private` on subroutines and variables** (including nested ones) unless the user asks for it. Everything is public by default in Prog8 — follow that convention.
- `inline` keyword for subroutines to suggest inlining
- No **function overloading** (except builtins) and no **polymorphism** in general. This means you must call specific routines for different types (e.g., `txt.print_ub(val)` vs `txt.print_w(val)` instead of a generic `print(val)`). Cannot use builtin names (msw, lsw, msb, lsb, mkword, mklong, peek, peekw, peekl, etc.) as variable/sub names
- Can return 0, 1, or multiple values: `a, b, c = routine()`. Use `void` to skip: `void routine()`, `a, void, c = routine()`
- Nested subroutines access parent scope variables directly

## Assembly Subroutines (asmsub)
For low-level assembly that gets arguments via registers and returns values in registers.
- **Syntax**: `[private] [inline] asmsub subname(params) [clobbers(regs)] [-> returns] { %asm {{ ... }} }`
- **Body Restriction**: The body of an `asmsub` **must only contain** a single `%asm {{ ... }}` node. Regular Prog8 statements or nested blocks are NOT allowed.
- **Parameters**: `type name @register` (e.g., `ubyte val @A`, `uword addr @AX`, `float f @FAC1`).
- **Return Values**: `-> type @register` (e.g., `-> ubyte @A`, `-> bool @Pz` for immediate branch use).
- **Registers**:
    - **8-bit**: `@A`, `@X`, `@Y`
    - **16-bit**: `@AX`, `@AY`, `@XY` (register pairs)
    - **Float**: `@FAC1`, `@FAC2` (Floating Point Accumulators)
    - **Virtual**: `@R0`–`@R15` (16-bit), `@R0R1`–`@R14R15` (32-bit combined)
    - **Status Flags (for returns)**: `@Pc` (Carry), `@Pz` (Zero), `@Pv` (Overflow), `@Pn` (Negative)
- **Clobbers**: `clobbers (A, X, Y)` — list all hardware registers modified by the routine.
- **Parameter names** are for documentation and type checking only. Use the registers in your assembly code.
- **Inlining**: `inline asmsub` will paste the assembly code directly at the call site, avoiding `jsr`/`rts` overhead.

## External Subroutines (extsub)
Used to call routines at fixed memory addresses (like ROM KERNAL routines or third-party drivers).
- **Syntax**: `[private] extsub [@bank <value>] address = subname(params) [clobbers(regs)] [-> returns]`
- **Address**: Can be a hex literal (`$C000`) or a constant expression.
- **Bank (optional)**: `@bank <integer>` (constant bank) or `@bank <identifier>` (variable bank).
- **No Body**: Unlike `asmsub`, `extsub` has no `{ }` body; it just maps a signature to an address.
- **Example**:
  ```prog8
  ; CX16 KERNAL CHROUT
  extsub $FFD2 = chrout(ubyte char @A) clobbers(A, X, Y)

  ; Routine in a specific RAM bank
  extsub @bank 10 $C09F = audio_init() clobbers(A, X, Y) -> bool @Pc
  ```

## Assembly Programming Details
- **Symbol prefixes**: `p8v_` (variables), `p8s_` (subroutines), `p8b_` (blocks), `p8c_` (constants), `p8l_` (labels), `p8t_` (structs), `p8_` (other)
- **Fully qualified names**: `p8b_blockname.p8v_varname`, `p8b_blockname.p8s_subname.p8v_localvar`
- **Within a `.proc`**, short names often work. `%option no_symbol_prefixing` disables prefixes (used by `cbm`, `cx16`, `txt`)
- **Split word arrays**: append `_lsb` / `_msb` to variable name (e.g., `p8v_myarray_lsb`)
- **CX16 target**: use 65C02 instructions (STZ, PHX, etc). Others: 6502 only
- **Assembly syntax**: 64tass. `.proc`/`.pend`, `_label` for locals, `.byte`/`.word`/`.dword` for data, `=` for equates
- **Instructions** like `rol`, `ror`, `asl`, `lsr` require explicit operand: `rol a`
- **Anonymous labels**: `+` (forward), `-` (backward), branch with `+`, `++`, `+++` or `-`, `--`, `---`
- **Register Aliases**: It's common to define aliases at the start of an `asmsub`:
  ```prog8
  asmsub my_routine(uword ptr @AX) {
      %asm {{
          ptr_lo = p8zp_scratch_w1
          ptr_hi = p8zp_scratch_w1+1
          sta ptr_lo
          stx ptr_hi
      }}
  }
  ```

## Standard Library
- Find routines, functions, variables, modules and signatures in the symbol dump file for the given compilation target. 
  - Online location: https://prog8.readthedocs.io/en/latest/libraries.html#low-fi-variable-and-subroutine-definitions-in-all-available-library-modules  they are linked there 1 for each compilation target
  - Structure: builtin functions, then module sections with variables/constants (`type name`) and subroutines (`name (params) -> returntype`)
- Text output: `textio` module (`txt.print`, `txt.chrout`, `txt.print_b`/`_ub`/`_w`/`_uw`/`_l`/`_bool`, `txt.print_f` for floats, `txt.spc()`, `txt.nl()`). **Note: Prog8 has no function overloading**, so you cannot use `txt.print(number)` — you must call the specific routine for the type (e.g., `txt.print_ub(val)` for an unsigned byte).
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
- **No bare `{ }` blocks** like C/Java
- Indentation: 4 spaces for .p8 and .asm files (no tabs)
- Character encoding: 6502 targets use PETSCII by default (call `txt.lowercase()` at start for lowercase). Virtual target uses ISO (`%encoding iso` + `txt.iso()`)
- Array size inferred from initializer: `str[] types = ["a", "b", "c"]`
- Enums: `Enum::Value` syntax (double colon), declared inside a block. They are syntactic sugar for a list of `const` declarations, not a type. Use enums for related values, `const` for standalone
- Avoid `globals.XXXX` — move constants closer to where they're used
- Member access through pointers: use `.` for both direct and pointer access. The compiler infers the type. For complex assignment targets, `^^` may be needed: `ptr^^.field = value`
- Qualified names: must use full path from top level (e.g., `cx16.r0`, not relative)
