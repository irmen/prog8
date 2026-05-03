# Prog8 Language Information

This file contains general information and instructions about the Prog8 programming language.

## General & Setup
- an overview of the language features can be found in the documentation file docs/source/introduction.rst
- the syntax and grammar is specified in an ANTLR4 grammar file found in the parser directory
- a program consists of a 'main' block containing the 'start' subroutine entry point, and zero or more other subroutines. Additional blocks and subroutines in those can be present too.
- module imports are done using "%import modulename" – there is NO "as" aliasing (e.g., don't write "%import foo as bar"). Use the module's defined prefix (e.g., textio defines "txt", diskio defines "diskio"). Example: "%import textio" imports the textio module, but it defines the "txt" prefix where all the routines are in.

## Datatypes & Variables
- available primitive datatypes: bool, byte, ubyte, word, uword, long, float, str. ubyte/uword are unsigned. long=4 bytes (SIGNED only, no unsigned long yet), float=5-byte Microsoft format, str=0-terminated ubytes (max 255 chars).
- **float requires import**: When using `float` variables or operations, you **must** add `%import floats` at the top of your source file. Without this import, the compiler will error with "floating point used, but that is not enabled via options".
- **printing floats**: Use `txt.print_f(floatvalue)` to print float values. This is available via `%import textio`.
- there are also arrays (max 256 bytes, or 512 for split word arrays), pointers, and structs. Prog8 does not yet have by-value struct variables, only pointer to structs. Pointers can point to primitive types or struct types. Use `memory()` + pointers for data larger than array limits.
- **struct field types**: Structs can only contain simple types (bool, byte, ubyte, word, uword, long, float) and `str`. Arrays are NOT allowed as struct fields. Note that `str` in a struct is equivalent to `^^ubyte` (a pointer to a zero-terminated byte array).
- **word and pointer arrays split by default**: LSB and MSB bytes stored in separate arrays for efficient 6502 access. This applies to both `word`/`uword` arrays and pointer arrays. With `@nosplit` this can be overridden to use regular sequential storage.
- prog8 has C-style pointer arithmetic when adding or subtracting integers from pointers. **pointer syntax differs from C**: Dereference with `@(ptr)` or `ptr[index]`.
- while there are larger than byte datatypes, the intended compiler target is a 6502 CPU system which is 8 bit so operations on larger datatypes are expensive. Words are still somewhat okay, but longs and floats in particular are very inefficient. Try to avoid them unless needed for correctness.
- all variables (including parameters) are statically allocated exactly once; there is no call stack, so recursion and reentrancy are not possible by default. All variables are zero-initialized (globals at program start, locals on subroutine entry).
- variables should not be placed in zeropage (with `@zp` and `@requirezp`) often, because there is only limited zeropage memory space, *except* for pointer variables: those should usually be in zeropage.
- **`private` symbols**: Use the `private` keyword to restrict a variable (or subroutine) access to its own block. By default, everything is public.
- **math performance**: Integer trigonometry functions like `math.sin8`, `math.cos8`, and their variants are implemented using **Lookup Tables (LUTs)** in the standard library and are very fast. Floating point `floats.sin`/`cos` are ROM-calls or VM builtins and are much slower.
- **@shared variables**: Use `@shared` to mark a variable as "might be used by some other code that I can't see, so don't optimize it away". This is usually the case when it is used in some assembly code, which the prog8 compiler itself cannot parse to track variable usages.
- **Zeropage scratch variables:** The compiler provides these predefined zeropage scratch variables: `P8ZP_SCRATCH_B1` (byte), `P8ZP_SCRATCH_REG` (byte), `P8ZP_SCRATCH_W1` (word), `P8ZP_SCRATCH_W2` (word), `P8ZP_SCRATCH_PTR` (word). **No other zeropage locations can be freely used**. Assembly routines must only use these predefined scratch variables. If additional storage is needed, define regular variables in the BSS section instead. On the cx16 the R0-R15 virtual registesr are also in zeropage so you can use them there too.
- **Virtual registers** (`cx16.r0` - `cx16.r15`): Available on ALL targets, not just CX16, BUT only on the CX16 they are located in Zero Page. They're fast 16-bit global variables but NOT preserved across subroutine calls. R12-R15 are especially dangerous: long operations may clobber them without warning. In IRQ handlers, save/restore with `cx16.save_virtual_registers()` / `cx16.restore_virtual_registers()`. You can give them descriptive names using aliases: `alias score = cx16.r7` but using regular variables is preferred.

## Recursion and Stack Management
**Prog8 has no call stack for variable storage** - all variables (including parameters and locals) are statically allocated. This means recursive subroutine calls will overwrite each other's variables. If you need recursion or reentrancy, you must manually manage state:
1. **CPU hardware stack**: Use builtins `push()`/`pushw()`/`pushl()`/`pushf()` and `pop()`/`popw()`/`popl()`/`popf()`. Save local state before recursive calls and restore after.
2. **Software stacks**: Use `buffers.stack` or `buffers.smallstack` from the `buffers` library. `stack` stores `uword` values, `smallstack` stores `ubyte` values. Both provide `push_b()`/`push_w()` and `pop_b()`/`pop_w()` routines. Use these when you can't or don't want to use the CPU hardware stack.
3. **Iterative rewrite (preferred)**: Many recursive algorithms can be rewritten as simple loops. Bisection, binary search, and similar divide-and-conquer algorithms work well as `repeat` loops with explicit bounds tracking. **Prefer this approach when possible**; it avoids all stack management overhead.

## Strings, Arrays & Pointers
- **size limitation**: `str` and array types are limited to 256 bytes maximum. This means for example that `long[]` is limited to 64 entries (64 × 4 bytes = 256 bytes). For larger data, use `memory()` + pointers.
- **2D arrays**: Supported using `type[rows][cols] name` syntax. Access with `name[r][c]`. It desugars to a flat 1D array access: `name[r * numCols + c]`. Only 2-dimensional arrays are supported; 3D or higher are not. Chained indexing `[r][c]` only works on variables explicitly declared as 2D arrays. **Total memory size is still limited to 256 bytes** (rows × columns × element_size ≤ 256), same as 1D arrays. (Split word arrays can have up to 256 elements because they use two separate 256-byte chunks). Examples: `ubyte[16][16]` (256 bytes) is max, `long[8][8]` (256 bytes) is max. **Initialization** must be a flat list of values: `ubyte[2][3] m = [1,2,3,4,5,6]`. Nested lists like `[[1,2,3],[4,5,6]]` are NOT supported.
- **pass-by-value vs pass-by-pointer**: A `str` or array variable is accessed *by value* but **ONLY in the subroutine where it is declared**. When passed as a parameter to a subroutine, only the pointer (address) is passed. In the receiving subroutine, a `str` parameter is actually a `^^ubyte` pointer, and an array parameter is actually just a pointer to the element type (e.g., `^^long` for `long[]`).
- **no const pointers or pointer-to-pointer**: Currently there is no support for `: const` pointers or pointers to pointers.
- **parsing limitations**: There are some syntax parsing limitations that fail on certain pointer dereferencing and indexing expressions. One example is that you cannot write `pointer[index].field` as an assignment target (it is fine as an expression). You need to explicitly add the pointer dereference operator `^^` like so: `pointer[index]^^.field = 9999`. The same expression without array indexing is fine as an assignment target.
- **typed vs untyped pointers**: Prog8 has *typed* pointers but also supports the legacy "untyped" pointer where every pointer is basically just a `uword` containing the memory address. C-style pointer arithmetic only works on typed pointers; for "uword" pointers it always considers the element it points to be a single `ubyte`. Prog8 allows freely converting between both forms in an assignment.
- **pointer arithmetic behavior**: When using typed pointers, arithmetic is scaled by the size of the pointed-to type. For example, adding `1` to a `^^word` pointer increments the address by `2`. For `uword` (untyped) pointers, adding `1` always increments the address by `1`.
- **pointer syntax**: The pointer declaration and dereference syntax is similar to Pascal's but Prog8 requires a double `^^` (because single `^` is already a taken operator). Note that `pointer[0]` is equivalent to `pointer^^`.
- **address-of operators**: The `&` operator returns the *untyped* address of its argument (a `uword`), whereas the `&&` operator returns a *typed pointer* to its argument. Note that `&&` is **NOT** the logical AND operator in Prog8: that is written as `and`.
- **static memory allocation only**: Prog8 only has *static* memory allocation. The `memory()` builtin function returns the address of a statically reserved memory block (named with the given name). It is possible to statically initialize struct variables with the syntax `^^StructType pointer = ^^StructType:[1,2,3,4]`. The `^^StructType:` may be omitted from the initializer list if it is easy to infer it from the target variable type. The initializer list may be empty which means the struct instance is zeroed out *but only at program startup*. Real "dynamic" memory allocation is impossible, but it can be emulated with a simplistic "arena allocator" that just keeps track of a large `memory()` slab internally.
- **poking and peeking**: `@(ptr)` as LHS of an assignment is equivalent to `poke(ptr, RHS)`. `@(ptr)` as RHS of an assignment (i.e., as an *expression*), is equivalent to `peek(ptr)`. This is how you read/write single **byte** values at a memory address. **Note:** `@(ptr)` is strictly for bytes only - for other datatypes you must use the explicit builtin functions: `peekw(ptr)`/`pokew(ptr, value)` for words, `peekl(ptr)`/`pokel(ptr, value)` for longs, `peekf(ptr)`/`pokef(ptr, value)` for floats, and `peekbool(ptr)`/`pokebool(ptr, value)` for booleans. There is no `@()` syntax equivalent for these other datatypes.

## Logic & Control Flow
- **Logical operators** (short-circuit, boolean only): `and`, `or`, `xor`, `not`
  - Use in conditions: `if x == 0 or y > 10 { ... }`
  - Short-circuit: In `a and b`, if `a` is false, `b` is NOT evaluated. In `a or b`, if `a` is true, `b` is NOT evaluated.
  - This is important when `b` has side effects or could cause errors.
- **Bitwise operators** (work on integer bits): `&`, `|`, `^`, `~`, `<<`, `>>`
  - Use for bit manipulation: `mask = mask | FLAG_ACTIVE`
  - Test bits: `if (mask & FLAG) != 0 { ... }`
  - **Common mistake**: Don't use `and`/`or` for bitmask operations - use `&`/`|` instead!
- CPU status flags: if_cs, if_cc, if_z, if_nz, etc. compile to single 6502 branch instructions.
- use 'when' statements with choice blocks instead of multiple 'if' statements.
- use 'repeat' instead of loops when iteration count is not needed.
- use if-expressions instead of if-statements for simple value assignments based on a choice.
- 'defer' defers statement execution until scope exit.
- 'goto' with labels and jump lists are allowed for optimal code.

## Subroutines & Return Values
- **private/public access**: Symbols are public by default. Use the `private` keyword to restrict a variable or subroutine access to its own block.
- **inline subroutines**: Subroutines can be marked with `inline` to suggest to the compiler that it should inline the code instead of calling it. This is especially useful for small subroutines. Use `private inline sub ...` if it's only used within its block.
- **no function overloading**: Each subroutine must have a unique name (except for some builtin functions).
- subroutines can return 0, 1 or more return value(s). They can be assigned to multiple variables in a single multi-variable assignment: a,b,c = routine(). Values can be skipped using 'void'.
- **the `void` keyword has two forms:** (1) prefix form `void routine()` suppresses all return values from a subroutine call, (2) assignment form `a, void, c = routine()` skips specific return values in multi-return assignments.
- subroutines can be nested. Nested subroutines have direct access to all variables defined in their parent scope. They access them via the parent's static symbols (no display or stack links), as all variables are statically allocated.

## Assembly Subroutines (asmsub)
- **Purpose**: For kernel (ROM) routines or low-level assembly routines that get arguments via specific registers (sometimes even via processor status flags like Carry)
- **parameter passing**: Parameters MUST specify which CPU register or virtual register receives them:
  - `@A` - Accumulator (8-bit)
  - `@X` - X register (8-bit)
  - `@Y` - Y register (8-bit)
  - `@AX` - A (low) and X (high) combined (16-bit word)
  - `@AY` - A (low) and Y (high) combined (16-bit word)
  - `@R0`-`@R15` - CX16 virtual registers (16-bit), e.g., `@R0` = cx16.r0
  - `@FAC1`, `@FAC2` - Floating point registers
  - `@Pc` - Carry flag (for bool parameters)
  - `@Pz` - Zero flag (for bool parameters)
- **return values**: Specify register for return value after `->`, e.g., `-> ubyte @A` or `-> uword @AY`. Return values can also be via processor status flags (e.g., `-> bool @Pz`), allowing immediate use of branch instructions like `if_z` or `if_cs`
- **clobbers**: List all registers modified by the routine: `clobbers (A, X, Y)` or `clobbers (A, Y)`
- **IMPORTANT: Parameter names in asmsub are NOT variables**: unlike regular subroutines, asmsub parameter names do NOT create subroutine parameter variables. The caller does NOT store values into variables; values are passed directly in registers. The parameter names are only for documentation. In the assembly code, you MUST use the registers specified in the signature.
- **Symbolic aliases**: For clarity, create aliases at the start of assembly: `x1 = cx16.r0` or `x1_lo = cx16.r0L`. If you need to preserve register values, store them explicitly in BSS variables or zero-page locations.
- **CPU instruction set differences**: Only the CommanderX16 target (cx16) can use 65C02 assembly instructions such as STZ. The other targets (C64, C128, PET32) can only use original 6502 instructions.
- **Example**: 
  ```prog8
  asmsub line(uword x1 @R0, ubyte y1 @A, uword x2 @R1, ubyte y2 @Y) clobbers (A, X, Y) {
      %asm {{
          x1 = cx16.r0      ; alias for parameter
          x2 = cx16.r1      ; alias for parameter
          ; MUST use registers (or aliases), NOT parameter names directly
          lda  x1           ; NOT "lda _x1" - there is no _x1 variable!
      }}
  }
  ```

## Accessing Prog8 symbols from Assembly
When writing assembly code (in `%asm` blocks or `asmsub`), you often need to refer to Prog8 variables, subroutines, or constants. The compiler prefixes these symbols and replaces dots with underscores (except for block/subroutine boundaries where it may keep the dot):
- **Variables and parameters**: Prefixed with `p8v_` (e.g., `p8v_myvar`).
- **Subroutines**: Prefixed with `p8s_` (e.g., `p8s_mysub`).
- **Blocks**: Prefixed with `p8b_` (e.g., `p8b_myblock`).
- **Constants**: Prefixed with `p8c_` (e.g., `p8c_myconst`).
- **Labels**: Prefixed with `p8l_` (e.g., `p8l_mylabel`).
- **Struct types**: Prefixed with `p8t_` (e.g., `p8t_MyStruct`).
- **Enum members**: Prefixed with `p8c_` and include the enum name (e.g., `p8c_MyEnum_Member`).
- **Other symbols**: Prefixed with `p8_`.

**Fully qualified names**: Symbols are usually fully qualified. For example, a variable `myvar` in block `myblock` becomes `p8b_myblock.p8v_myvar`. If it's inside a subroutine `mysub`, it becomes `p8b_myblock.p8s_mysub.p8v_myvar`. Note that dots are used as separators between the prefixed components.
**No-prefixing option**: Blocks can use `%option no_symbol_prefixing` to disable this behavior. Most standard library modules (like `cbm`, `cx16`, `txt`) use this, which is why you can refer to `cbm.CHROUT` directly in assembly without prefixes.
**Local scoping**: Within a `.proc` (subroutine) in assembly, you can often use the short name (e.g., `p8v_myvar`) if it was defined in that same subroutine, as the assembler handles the scoping.
**Split word arrays**: These are special. They are emitted as two separate byte arrays. To access them in assembly, append `_lsb` and `_msb` to the variable name (e.g., `p8v_myarray_lsb` and `p8v_myarray_msb`).

## Standard library
- **to discover what modules and routines are available, FIRST consult docs/source/_static/symboldumps/** - skeleton files per target list ALL modules, subroutines, and builtin functions with their signatures.
- **symboldump structure**: Compiler version info, then **BUILTIN FUNCTIONS** (names only), then **LIBRARY MODULE NAME:** sections. Within each module `{...}`: variables/constants show `type  name` (with `@shared`/`@requirezp`/`@AY` annotations), subroutines show `name  (params) -> returntype` (with `clobbers (X,Y)` for asm routines).
- **standard library source code** is in 'res/prog8lib' directory. See docs/source/libraries.rst for details.
- **text output** via 'textio' module (txt.print, txt.chrout, etc.), math in 'math', string conversions in 'conv'. **Note:** conv uses `str_<type>` for number-to-string (e.g., `str_uword`), `str2<type>` for string-to-number. **For printing numbers use txt routines directly**: `txt.print_b` (byte), `txt.print_ub` (ubyte), `txt.print_w` (word), `txt.print_uw` (uword), `txt.print_l` (long), `txt.print_bool` (bool).
- **character checks**: The `strings` module has functions like `isdigit()`, `isxdigit()`, `isupper()`, `islower()`, `isletter()`, `isspace()`, and `isprint()`.
  - **Single character output**: Use `txt.chrout(char_byte)` instead of `txt.print("c")` - it's faster and more direct.
  - **Spaces and newlines**: Use `txt.spc()` for a space character and `txt.nl()` for a newline. Avoid `txt.print(" ")` or `txt.print("\n")` as these create unnecessary string overhead.

## Syntax & Formatting
- **numeric literal syntax**: `$` prefix for hex (`$FF` not `0xFF`), `%` prefix for binary (`%1010` not `0b1010`). Underscores allowed for readability: `25_000_000`. No leading-zero octal notation. **No type suffixes**: there are no suffixes for word or long literals (e.g. `0L` or `0W` are invalid). If you need to disambiguate a literal between byte/word/long types, cast it to the desired type: `0 as long`. Otherwise the type is determined by context. Note that using 4-digit hex notation such as `$0000` IS interpreted as a `uword` value.
- **for loop syntax**: `for i in 0 to 10 { ... }` (use downto when counting down) - NOT `for i = 0 to 10` or C-style `for(i=0; i<10; i++)`. Loop variables must be declared separately before the loop - inline declaration like `for ubyte i in 0 to 10` is not supported.
- **semicolons start comments**: `; this is a comment` - they do NOT end statements. There is NO statement separator (unlike C/Java's `;`). One statement per line only. Multi-line comments use `/* ... */`.
  - **CRITICAL: `;` is NOT a statement separator!** It begins a comment that extends to end of line. Writing `x = 1; y = 2` does NOT assign two variables - it assigns `x = 1` and then ignores everything after `;` as a comment. **Each statement must be on its own line.**
- **no `elif` keyword**: Prog8 does NOT have `elif`/`elsif`/`elseif` for chaining if-else conditions. Use nested `else { if ... }` instead:
  ```prog8
  ; WRONG - elif doesn't exist
  if a { ... } elif b { ... } elif c { ... }

  ; RIGHT - use nested else + if
  if a { ... } else {
      if b { ... } else {
          if c { ... }
      }
  }
  ```
  This is a **widespread mistake** for developers coming from Python/C/Java backgrounds.
- **Indentation**: See `.editorconfig` (4 spaces for .p8 and .asm files, no tabs).
- **The assembly source code uses 64tass syntax, NOT ca65/cc65 or other assemblers.** Key 64tass syntax: `.proc`/`.pend` for procedures, `_label` for local labels, `.byte`/`.word`/`.dword` for data, `= ` for equates, zero-page variables defined with `=`. **Instructions like `rol`, `ror`, `asl`, `lsr` require an explicit operand** - use `rol a`, `ror a`, etc. for the accumulator, not just `rol` or `ror`.
- **Character encoding**: 6502 targets use **PETSCII** by default. The `virtual` target uses **ISO** encoding.
  - **6502 targets**: Use `txt.lowercase()` at program start for lowercase display.
  - **Virtual target**: Use `%encoding iso` directive and call `txt.iso()` at program start.
  - **Exception for x16emu debugging**: When running with `x16emu -echo iso` to see console output, use ISO encoding even on 6502 targets so the echoed text is readable.
- **String arrays**: Use `str[N]` for arrays of strings (e.g., `str[12] types = ["sword", "axe", ...]`). Access with `types[i]`.
- **String buffer pre-allocation**: **CRITICAL**: Empty strings don't allocate space! Always pre-allocate:
  - WRONG: `str buffer = ""` (no space allocated, `strings.append()` will fail)
  - RIGHT: `str buffer = "." * 50` (allocates 50 characters)
- **String assignment**: You cannot assign a new value to a `str` variable after its declaration (e.g., `buffer = "new text"` is an error). You MUST use `strings.copy()` or `strings.ncopy()`.
- **String concatenation pattern**: Use `strings` module functions with pre-allocated buffers:
  ```prog8
  %import strings
  str buffer = "." * 50          ; pre-allocate
  void strings.copy(source, buffer)   ; initialize
  void strings.append(buffer, "text") ; concatenate
  ```
  **WARNING**: String concatenation is EXPENSIVE on 6502! Only use when you truly need a combined string. If fragments can be handled separately (e.g., printing multiple strings in sequence), do NOT concatenate just process each fragment separately:
  ```prog8
  ; EXPENSIVE - don't do this unless necessary
  void strings.append(buffer, prefix)
  void strings.append(buffer, suffix)
  txt.print(buffer)
  
  ; CHEAP - prefer this when possible
  txt.print(prefix)
  txt.print(suffix)
  ```
- **Use `len()` for array sizes**: Don't hardcode array lengths - use `len(array)`:
  ```prog8
  ; WRONG: magic number
  ubyte i = math.rnd() % 20

  ; RIGHT: self-documenting
  ubyte i = math.rnd() % len(weapons.prefixes)
  ```
- **Enums**: Prog8 enums are declared inside a block and use `Enum::Value` syntax (double colon, not dot):
  ```prog8
  enum Priority {
      LOW = 1,
      NORMAL,      ; auto-numbered to 2
      HIGH,        ; auto-numbered to 3
      EXTREME = 255
  }

  ; Generates these constants:
  const ubyte Priority::LOW = 1
  const ubyte Priority::NORMAL = 2
  const ubyte Priority::HIGH = 3
  const ubyte Priority::EXTREME = 255

  ; Usage:
  ubyte p = Priority::HIGH  ; NOT Priority.HIGH
  ```
  **When to use enums vs const**: Use enums for sets of related named values (states, types, classes). Use `const` for standalone values:
  ```prog8
  ; GOOD: enum for related choices
  enum CharClass {
      FIGHTER = 1,
      MAGE = 2,
      CLERIC = 3
  }

  ; GOOD: const for standalone values
  const ubyte MIN_HP = 1
  const ubyte BASE_AC = 10
  ```
  **Note**: Name accesses enum values directly within their block. For values needed across multiple blocks, use `const` in a shared block instead.
- **Avoid `globals.XXXX` code smell**: If you find yourself prefixing many constants with `globals.`, consider:
  1. Moving constants closer to where they're used (inside the relevant block)
  2. Using literals directly for obvious values (like `3` for 3d6)
- **Array size inference**: When declaring an array with an initializer list, you don't need to specify the size; Prog8 infers it automatically:
  ```prog8
  ; Explicit size (works but verbose)
  ubyte[12] types = ["sword", "axe", "bow", ...]
  
  ; Inferred size (cleaner, preferred)
  str[] types = ["sword", "axe", "bow", "staff", "mace", "dagger"]
  ```
  This is especially useful for string arrays and lookup tables. Use `len(array)` to get the size when needed.
- **Use strings library for character operations**: The `strings` module has functions for character manipulation and checks (case conversion, character class tests, etc.). Use these instead of manual ASCII/PETSCII arithmetic.
- **String functions return useful lengths**: Several `strings` library routines return the length of the string they operated on. This return value is often voided, but capturing it avoids redundant `len()` calls:
  ```prog8
  ; returning length, Useful for: repeated appends, tracking buffer usage, avoiding redundant len() calls
  len = strings.copy(dest, src)     ; returns copied length
  len = strings.append(buf, text)   ; returns resulting length
  len = strings.upper(mystr)        ; returns string length
  ```

## Other Key differences from other languages (C, Python, etc.)
- **type casting syntax**: Use `expression as <type>` to cast (e.g., `bytevar as word`, `(a+b) as uword`). This is required for type conversions. Note that `as` has very low precedence, lower than arithmetic operators. So `a + b as long` is parsed as `(a + b) as long`. Use parentheses if you want to cast operands before an operation: `(a as long) * b`.
- **no automatic type widening**: `byte*byte=byte` (may overflow!), `word*word=word`, etc. Explicitly cast operands: `word result = (bytevar as word) * 1000`. Hex literals with 4-digit full width (e.g., `$0040`) are interpreted as `uword`. Compiler does not warn by default. **Use `as <type>` for all casts.**
- **no block scope**: `for`, `if/else` blocks do NOT introduce new scope. Only subroutines introduce scope. Variables declared anywhere in a subroutine are hoisted to the top.
- **no bare blocks**: Prog8 does not have standalone `{ ... }` blocks like C/C++/Java. Control structures (`if/when/for/repeat`) provide grouping but NOT scoping; they cannot create temporary variable lifetimes. Only subroutines introduce scope.
- **qualified names from top level**: Must use full qualified names (e.g., `cx16.r0`), not relative imports.
