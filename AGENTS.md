# Agents.md

Context and instructions for AI Agents to work on this project.

## Project Overview
- This project is a compiler for the Prog8 programming language.
- Prog8 is a programming language primarily targeting 8-bit retro systems with the 6502 CPU, such as the Commodore 64, Commodore 128, and Commander X16.
- The compiler has a 6502 code generator backend, and an IR code generator.
- The IR code is meant to be used in a new machine specific code generator backend (primarily 6502 but maybe 68000 as well later)
- The compiler includes a simple 'virtual machine' that can execute the IR code directly via interpretation.
- Prog8 source files have .p8 extension - these are *not* LUA or PICO-8 source files in this case!
- Prog8 source files are a "module" that can contain 1 or more "blocks". They can also import other modules, from internal library files or from source files on the filesystem.
- The prog8 compiler is written mostly in Kotlin, those files have the .kt extension.
- The standard library is mostly written in Prog8 and assembly code, and can be found in the "compiler" module, in the 'res/prog8lib' directory.
- Kotlin version 2.3 is used for the compiler implementation.
- Java 17 is used as Java runtime version.
- ANTLR4 version 4.13 is used for the parser implementation.
- Dependent library versions can be found in 'build.gradle.kts' and in the IntelliJ IDEA configuration files in .idea/libraries
- The compiler main entrypoint is in the "compiler" module, in src/prog8/CompilerMain.kt

## Compilation Flow (High-Level)
```
Source → parseMainModule() → processAst() → optimizeAst() → postprocessAst()
       → Simple AST → Code Generator → Assembly → .prg
```

**Key phases in `compiler/src/prog8/compiler/Compiler.kt`:**
- `parseMainModule()` - Import modules, parse to Compiler AST
- `processAst()` - Semantic analysis, constant folding, type casting, validation
- `optimizeAst()` - Dead code elimination, inlining, statement optimization
- `postprocessAst()` - Memory layout, final transformations
- Code generation - Simple AST → IR or 6502 assembly

**Understanding this order is important:** For example, `ConstantIdentifierReplacer` runs during `processAst()`, so by the time `AstChecker` runs at the end of that phase, identifier references have already been replaced with their actual values.

## DEBUGGING TIP: Use `-noopt` to isolate problems
When debugging compiler issues, **FIRST try compiling with the `-noopt` switch**:
- **Problem gone with `-noopt`**: Issue is in **optimization phases** (`optimizeAst()`, `UnusedCodeRemover`, `Inliner`, etc.)
- **Problem persists with `-noopt`**: Issue is in **parsing, semantic analysis, symbol table, or code generation**

## IMPORTANT: SymbolTable cachedFlat cache
The `SymbolTable` class has a **cached `flat` property** for fast symbol lookups.

**CRITICAL:** If you modify the AST **after** SymbolTable construction, you **MUST** call `symbolTable.resetCachedFlat()` or lookups will fail!

## IMPORTANT: Virtual Target (IR Codegen) Issues
For problems that **ONLY occur with the 'virtual' target**, **ONLY modify these modules**:
- `codeGenIntermediate` - IR code generator
- `intermediate` - IR representation and file I/O  
- `virtualmachine` - VM that executes IR code

**DO NOT modify** `compilerAst`, `simpleAst`, `codeGenCpu6502`, etc. The IR codegen has its own separate handling for symbol tables, AST transformations, and unused code removal.

## CRITICAL: NO FORMATTING
- NEVER run formatters (black, ruff, prettier, etc.) after edits
- Preserve my exact indentation, line lengths, and spacing
- Make ONLY the requested changes, nothing else

## Prog8 language feature hints

### General & Setup
- an overview of the language features can be found in the documentation file docs/source/introduction.rst
- the syntax and grammar is specified in an ANTLR4 grammar file found in the parser directory
- a program consists of a 'main' block containing the 'start' subroutine entry point, and zero or more other subroutines. Additional blocks and subroutines in those can be present too.
- module imports are done using "%import modulename" *note*: this does *not* always mean that the scope "modulename" gets defined! Example: "%import textio" imports the textio module, but it defines the "txt" prefix where all the routines are in.

### Datatypes & Variables
- available primitive datatypes: bool, byte, ubyte, word, uword, long, float, str. ubyte/uword are unsigned. long=4 bytes (SIGNED only - no unsigned long yet), float=5-byte Microsoft format, str=0-terminated ubytes (max 255 chars).
- there are also arrays (max 256 bytes, or 512 for split word arrays), pointers, and structs. Prog8 does not yet have by-value struct variables, only pointer to structs. Pointers can point to primitive types or struct types. Use `memory()` + pointers for data larger than array limits.
- **struct field types**: Structs can only contain simple types (bool, byte, ubyte, word, uword, long, float) and `str`. Arrays are NOT allowed as struct fields. Note that `str` in a struct is equivalent to `^^ubyte` (a pointer to a zero-terminated byte array).
- **word arrays split by default**: LSB and MSB bytes stored in separate arrays for efficient 6502 access. With @nosplit this can be overridden to use regular sequential storage.
- prog8 has C-style pointer arithmetic when adding or subtracting integers from pointers. **pointer syntax differs from C**: Dereference with `@(ptr)` or `ptr[index]`.
- while there are larger than byte datatypes, the intended compiler target is a 6502 CPU system which is 8 bit so operations on larger datatypes are expensive. Words are still somewhat okay, but longs and floats in particular are very inefficient. Try to avoid them unless needed for correctness.
- all variables (including parameters) are statically allocated exactly once; there is no call stack, so recursion and reentrancy are not possible by default. All variables are zero-initialized (globals at program start, locals on subroutine entry).
- variables should not be placed in zeropage (with @zp and @requirezp) often, because there is only limited zeropage memory space, *except* for pointer variables: those should usually be in zeropage.
- **@shared variables**: Use `@shared` to mark a variable as "might be used by some other code that I can't see - so don't optimize it away". This is usually the case when it is used in some assembly code, which the prog8 compiler itself cannot parse to track variable usages.

### Strings, Arrays & Pointers - Important Peculiarities
- **size limitation**: `str` and array types are limited to 256 bytes maximum. This means for example that `long[]` is limited to 64 entries (64 × 4 bytes = 256 bytes). For larger data, use `memory()` + pointers.
- **pass-by-value vs pass-by-pointer**: A `str` or array variable is accessed *by value* but **ONLY in the subroutine where it is declared**. When passed as a parameter to a subroutine, only the pointer (address) is passed. In the receiving subroutine, a `str` parameter is actually a `^^ubyte` pointer, and an array parameter is actually just a pointer to the element type (e.g., `^^long` for `long[]`).
- **no const pointers or pointer-to-pointer**: Currently there is no support for `: const` pointers or pointers to pointers.
- **parsing limitations**: There are some syntax parsing limitations that fail on certain pointer dereferencing and indexing expressions. One example is that you cannot write `pointer[index].field` as an assignment target (it is fine as an expression). You need to explicitly add the pointer dereference operator `^^` like so: `pointer[index]^^.field = 9999`. The same expression without array indexing is fine as an assignment target.
- **typed vs untyped pointers**: Prog8 has *typed* pointers but also supports the legacy "untyped" pointer where every pointer is basically just a `uword` containing the memory address. C-style pointer arithmetic only works on typed pointers; for "uword" pointers it always considers the element it points to be a single `ubyte`. Prog8 allows freely converting between both forms in an assignment.
- **pointer syntax**: The pointer declaration and dereference syntax is similar to Pascal's but Prog8 requires a double `^^` (because single `^` is already a taken operator). Note that `pointer[0]` is equivalent to `pointer^^`.
- **address-of operators**: The `&` operator returns the *untyped* address of its argument (a `uword`), whereas the `&&` operator returns a *typed pointer* to its argument. Note that `&&` is **NOT** the logical AND operator in Prog8 - that is written as `and`.
- **static memory allocation only**: Prog8 only has *static* memory allocation. The `memory()` builtin function returns the address of a statically reserved memory block (named with the given name). It is possible to statically initialize struct variables with the syntax `^^StructType pointer = ^^StructType:[1,2,3,4]`. The `^^StructType:` may be omitted from the initializer list if it is easy to infer it from the target variable type. The initializer list may be empty which means the struct instance is zeroed out *but only at program startup*. Real "dynamic" memory allocation is impossible, but it can be emulated with a simplistic "arena allocator" that just keeps track of a large `memory()` slab internally.
- **poking and peeking**: `@(ptr)` as LHS of an assignment is equivalent to `poke(ptr, RHS)`. `@(ptr)` as RHS of an assignment (i.e. as an *expression*), is equivalent to `peek(ptr)`. This is how you read/write single **byte** values at a memory address. **Note:** `@(ptr)` is strictly for bytes only - for other datatypes you must use the explicit builtin functions: `peekw(ptr)`/`pokew(ptr, value)` for words, `peekl(ptr)`/`pokel(ptr, value)` for longs, `peekf(ptr)`/`pokef(ptr, value)` for floats, and `peekbool(ptr)`/`pokebool(ptr, value)` for booleans. There is no `@()` syntax equivalent for these other datatypes.

### Virtual Registers & Stack
- **Zeropage scratch variables:** The compiler provides these predefined zeropage scratch variables: `P8ZP_SCRATCH_B1` (byte), `P8ZP_SCRATCH_REG` (byte), `P8ZP_SCRATCH_W1` (word), `P8ZP_SCRATCH_W2` (word), `P8ZP_SCRATCH_PTR` (word). **No other zeropage locations can be used** - assembly routines must only use these predefined scratch variables. If additional storage is needed, define regular variables in the BSS section instead. You can also use the cx16 virtual registers (`cx16.r0`-`cx16.r15`) as temporary storage.
- the 16 'virtual registers' (cx16.r0 - cx16.r15) are available on ALL targets, not just CX16. They're fast 16-bit global variables but NOT preserved across subroutine calls. R12-R15 are especially dangerous: long operations may clobber them without warning. In IRQ handlers, save/restore with cx16.save_virtual_registers() / cx16.restore_virtual_registers(). You can give them descriptive names using aliases: `alias score = cx16.r7` but using regular variables is preferred.
- the CPU hardware stack can be manipulated via builtin functions: push(), pushw(), pushl(), pushf() and pop(), popw(), popl(), popf(). These can manually implement recursion if needed.

### Logic & Control Flow
- boolean operators: 'and', 'or', 'xor', 'not'. Bitwise: '&', '|', '^', '~', '<<', '>>'. See docs/source/programming.rst.
- **Short-circuit evaluation**: Logical `and` and `or` use short-circuit evaluation! In `a and b`, if `a` is false, `b` is NOT evaluated. In `a or b`, if `a` is true, `b` is NOT evaluated. This is important when `b` has side effects or could cause errors.
- CPU status flags: if_cs, if_cc, if_z, if_nz, etc. compile to single 6502 branch instructions.
- use 'when' statements with choice blocks instead of multiple 'if' statements.
- use 'repeat' instead of loops when iteration count is not needed.
- use if-expressions instead of if-statements for simple value assignments based on a choice.
- 'defer' defers statement execution until scope exit.
- 'goto' with labels and jump lists are allowed for optimal code.

### Subroutines & Return Values
- **everything is public**: No private/public modifiers. All symbols accessible via fully qualified names from anywhere.
- **no function overloading**: Each subroutine must have a unique name (except for some builtin functions).
- subroutines can return 0, 1 or more return value(s). They can be assigned to multiple variables in a single multi-variable assignment: a,b,c = routine(). Values can be skipped using 'void'.
- **the `void` keyword has two forms:** (1) prefix form `void routine()` suppresses all return values from a subroutine call, (2) assignment form `a, void, c = routine()` skips specific return values in multi-return assignments.
- subroutines can be nested. Nested subroutines have direct access to all variables defined in their parent scope.

### Standard library
- **to discover what modules and routines are available, FIRST consult docs/source/_static/symboldumps/** - skeleton files per target list ALL modules, subroutines, and builtin functions with their signatures.
- **symboldump structure**: Compiler version info, then **BUILTIN FUNCTIONS** (names only), then **LIBRARY MODULE NAME:** sections. Within each module `{...}`: variables/constants show `type  name` (with `@shared`/`@requirezp`/`@AY` annotations), subroutines show `name  (params) -> returntype` (with `clobbers (X,Y)` for asm routines).
- standard library source code is in 'res/prog8lib' directory. See docs/source/libraries.rst for details.
- text output via 'textio' module (txt.print, txt.chrout, etc.), math in 'math', string conversions in 'conv'. **Note:** conv uses `str_<type>` for number-to-string (e.g., `str_uword`), `str2<type>` for string-to-number. **For printing numbers use txt routines directly**: `txt.print_b` (byte), `txt.print_ub` (ubyte), `txt.print_w` (word), `txt.print_uw` (uword), `txt.print_l` (long), `txt.print_bool` (bool).

### Syntax & Formatting
- **numeric literal syntax**: `$` prefix for hex (`$FF` not `0xFF`), `%` prefix for binary (`%1010` not `0b1010`). Underscores allowed for readability: `25_000_000`. No leading-zero octal notation. **No type suffixes**: long literals are just regular numbers (e.g., `12345678` not `12345678L`), the type is determined by context (variable type or cast).
- **for loop syntax**: `for i in 0 to 10 { ... }` (use downto when counting down) - NOT `for i = 0 to 10` or C-style `for(i=0; i<10; i++)`
- **semicolons start comments**: `; this is a comment` - they do NOT end statements. There is NO statement separator (unlike C/Java's `;`). One statement per line only. Multi-line comments use `/* ... */`.
- Prog8 source files are indented with 4 spaces, no tabs. Assembly source files (*.asm) can use spaces or tabs.
- **The assembly source code uses 64tass syntax, NOT ca65/cc65 or other assemblers.** Key 64tass syntax: `.proc`/`.pend` for procedures, `_label` for local labels, `.byte`/`.word`/`.dword` for data, `= ` for equates, zero-page variables defined with `=`. **Instructions like `rol`, `ror`, `asl`, `lsr` require an explicit operand** - use `rol a`, `ror a`, etc. for the accumulator, not just `rol` or `ror`.

## Other Key differences from other languages (C, Python, etc.)
- **no automatic type widening**: `byte*byte=byte` (may overflow!), `word*word=word`, etc. Explicitly cast operands: `word result = (bytevar as word) * 1000`. Hex literals with full width (e.g., `$0040`) also promote. Compiler does not warn by default.
- **no block scope**: `for`, `if/else` blocks do NOT introduce new scope. Only subroutines introduce scope. Variables declared anywhere in a subroutine are hoisted to the top.
- **qualified names from top level**: Must use full qualified names (e.g., `cx16.r0`), not relative imports.

## Project Module Descriptions
- `compiler` - Main compiler entrypoint (src/prog8/CompilerMain.kt)
- `compilerAst` - Complex AST where most optimizations run
- `simpleAst` - Simplified AST used by code generator backends
- `parser` - ANTLR4 parser implementation
- `codeCore` - Core code generation utilities
- `codeGenCpu6502` - 6502 assembly code generator backend
- `codeGenIntermediate` - Intermediate representation (IR) code generator
- `codeGenVirtual` - Virtual machine code generator backend
- `codeOptimizers` - Optimization passes
- `intermediate` - IR components
- `virtualmachine` - VM that executes IR code
- `languageServer` - Language Server Protocol implementation
- `docs` - Documentation files
- `examples` - Example Prog8 programs

## Key Information
- never read the files and directories that are ignored via the .aiignore and .gitignore files
- never perform any git source control write/update/add/commit/branch operations. Read and status operations are allowed.
- **git log/history queries can be useful** for understanding when/why a feature was added or tracking down when a bug was introduced, but for locating code use grep_search or glob instead.
- Architecture decisions: separation of frontend/parser, IR intermediate representation, multiple backends
- **CPU instruction set differences**: Only the CommanderX16 target (cx16) can use 65C02 instructions such as STZ. The other targets (C64, C128, PET32) can only use original 6502 instructions.

# Dev environment tips

## Commands to build the compiler
- use the system installed gradle command instead of the gradle wrapper.

### Quick compile check (NO tests)
- **After changing compiler Kotlin source (.kt files)**: Use `gradle :compiler:compileKotlin` to quickly check for syntax/compile errors
- This compiles the compiler but **skips running tests** - much faster than `gradle build`
- Takes ~10-20s instead of ~45-60s for full build
- **Note:** This does NOT install the compiler - use `gradle installdist installshadowdist` after to actually use your changes

### When you MUST rebuild AND reinstall the compiler
**After ANY change to Kotlin compiler source code (.kt files) OR library files (compiler/res/prog8lib/**/*.p8 or .asm):**
- `gradle installdist installshadowdist` - Rebuilds and reinstalls the compiler with your changes
- **Without this step, your changes will NOT be reflected when running `prog8c`!**
- This compiles AND installs, but still skips running tests (faster than `gradle build`)

### When to run full build with tests
- **Before committing changes**: Always run `gradle build` to ensure all tests pass
- **After major refactoring**: Run `gradle build` to catch regressions
- **When debugging test failures**: Use `gradle test --tests "*TestName*"` for specific tests

### Build command summary

| Command | When to use | Time |
|---------|-------------|------|
| `gradle :compiler:compileKotlin` | Quick syntax check (no tests) | ~10-20s |
| `gradle installdist installshadowdist` | After compiler changes (to use them) | ~10-20s |
| `gradle build` | Before commits, after major changes | ~45-60s |
| `gradle test --tests "*Name*"` | Run specific tests | ~5-30s |

**Note:** Use system `gradle` command, not wrapper. Run `gradle clean` if you suspect stale artifacts.

### Running Tests

**For detailed error output (always use these):**
```bash
# Specific test class with full error details
gradle :compiler:test --tests "*TestName*" --info 2>&1 | grep -E "FAILED|AssertionError"

# Test filtering across all modules
gradle test -PtestFilter="*TestLookup*"

# For compilation errors in tests
gradle :compiler:compileTestKotlin --info 2>&1 | grep "^e:"
```

**Note:** Test config is centralized in root `build.gradle.kts`. Tests run in parallel. Only failures are shown.

### Two different workflows

**1. Testing your own Prog8 programs** (no compiler changes):
- No rebuild needed - just run `prog8c` directly
- Edit your `.p8` file → compile/run → check stdout output
- Example: `prog8c -target virtual -emu myprogram.p8`

**2. Testing compiler or standard library changes**:
- Rebuild required after every change to `.kt`, `.p8`, or `.asm` files in the compiler project
- Workflow: edit source → `gradle installdist installshadowdist` → test with `prog8c`
- Example: fix bug in `CodeGen6502.kt` → rebuild → `prog8c -target cx16 test.p8`

### Compilation Output Files
- `*.prg` - The final compiled program file for the target system (e.g., Commander X16)
- `*.asm` - Generated assembly code from the Prog8 source
- `*.list` - Generated full assembly listing file from the Prog8 source
- `*.p8ir` - Intermediate representation file, can be executed in the Virtual Machine
- `*.vice-mon-list` - Vice emulator monitor list file for debugging

## Commands to run the Prog8 Compiler
- the prog8c compiler executable can be found in the compiler/build/install/prog8c/bin folder (this is already added to the shell's path)
- **the `-check` switch performs a quick syntax/semantic check only - it will NOT produce any output files (no .prg, .asm, etc.)**. Use it only for fast error checking during development.
- **the `-noopt` switch DISABLES all optimizations** - useful for debugging to determine if a problem is caused by the optimizer. **Optimizations are ENABLED by default** (no flag needed).
- **prog8c uses single-dash command line options** (e.g., `-target`, `-noopt`, `-check`), NOT double-dash (`--target` is invalid).
- **the `-printast1` switch prints out the internal Compiler AST** after parsing and semantic analysis.
- **the `-printast2` switch prints out the optimized Simple AST** just before it goes to the code generator. This is useful for debugging optimizer issues.
- **the `-out outdir` switch sets an alternative output directory** for compiled files (.prg, .asm, .list, etc.). **By default, output files are written to the same directory as the source file**.
- `prog8c -target targetname input.p8` - Compile a Prog8 source file "input.p8" for the given target (cx16, c64, pet32, c128, virtual)
- `prog8c -target targetname -emu input.p8` - Compile and execute a prog8 file in the emulator for the given target (cx16, c64, pet32, c128, virtual)
- `prog8c -vm input.p8ir` - Execute an existing prog8 program, compiled in IR form, in the Virtual Machine
- `x16emu -scale 2 -prg input.prg` - Just load an existing compiled program in the CommanderX16 emulator. Ignore any errors and warnings, because the emulator doesn't produce any output on STDOUT.
- **CX16 debugging tip**: Use `x16emu -echo iso -prg input.prg` to make the emulator echo screen output (ISO-8859-16 encoded) to stdout. This allows you to see program output and debug messages in the terminal. You can pipe through `strings` or `iconv` to decode: `x16emu -echo iso -prg input.prg 2>&1 | strings` or `x16emu -echo iso -prg input.prg 2>&1 | grep -E "(PASS|FAIL|ERROR)"`.
- **IMPORTANT: Always use `sys.poweroff_system()` to exit the CX16 emulator cleanly!** Add `sys.poweroff_system()` at the end of your main program block - this exits x16emu automatically in most cases.
  **Note:** The `sys` module is always available, there is no need to import it ever.
- `x64sc input.prg` - run an existing compiled program in the Commodore-64 emulator. Ignore any errors and warnings, because the emulator doesn't produce any output on STDOUT.
- **Testing tip**: When writing and testing Prog8 programs, **use the `virtual` target** (e.g., `prog8c -target virtual -emu input.p8` or `prog8c -vm input.p8ir`). This is the preferred way to test because the virtual target can easily write output to stdout, making it simple to verify program behavior and check results.

## Commands to run tests
- `gradle test --tests "*TestName*"` - Run specific test classes
- `gradle build` - Full build of the compiler including running the full test suite
- Unit tests are written using KoTest, using FunSpec
- Several modules in the project contain unit tests, but most of them live in the "compiler" module in the 'test' directory.
- **when writing TEST programs, always add these directives at the top: `%zeropage basicsafe` and `%option no_sysinit`** - this keeps zeropage usage safe and skips system initialization for faster, simpler test programs.
- **When a test run fails**, the output says "There were failing tests. See the report at:" followed by a path like `file:///home/irmen/Projects/prog8/compiler/build/reports/tests/test/index.html`. **Read that HTML report** to quickly see which tests failed and their error messages!

## TODO Items
The file `docs/source/todo.rst` contains a comprehensive list of things that still have to be fixed, implemented, or optimized. **Use this to understand what features are NOT yet available** in the compiler or Prog8 language - if a user asks for something that's on the TODO list, you'll know it's not implemented yet and can explain the limitation.

## Code Style Guidelines
- **Minimal comments when making changes**: When modifying existing code, add only essential comments that explain *why* a change was made or document non-obvious behavior. **Do not add verbose comments** that restate what the code does - let the code speak for itself. Existing extensive comments should be preserved, but new changes should have minimal commentary.
