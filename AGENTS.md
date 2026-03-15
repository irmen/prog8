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

### Strings, Arrays & Pointers - Important Peculiarities
- **size limitation**: `str` and array types are limited to 256 bytes maximum. This means for example that `long[]` is limited to 64 entries (64 × 4 bytes = 256 bytes). For larger data, use `memory()` + pointers.
- **pass-by-value vs pass-by-pointer**: A `str` or array variable is accessed *by value* but **ONLY in the subroutine where it is declared**. When passed as a parameter to a subroutine, only the pointer (address) is passed. In the receiving subroutine, a `str` parameter is actually a `^^ubyte` pointer, and an array parameter is actually just a pointer to the element type (e.g., `^^long` for `long[]`).
- **no const pointers or pointer-to-pointer**: Currently there is no support for `: const` pointers or pointers to pointers.
- **parsing limitations**: There are some syntax parsing limitations that fail on certain pointer dereferencing and indexing expressions. One example is that you cannot write `pointer[index].field` as an assignment target (it is fine as an expression). You need to explicitly add the pointer dereference operator `^^` like so: `pointer[index]^^.field = 9999`. The same expression without array indexing is fine as an assignment target.
- **typed vs untyped pointers**: Prog8 has *typed* pointers but also supports the legacy "untyped" pointer where every pointer is basically just a `uword` containing the memory address. C-style pointer arithmetic only works on typed pointers; for "uword" pointers it always considers the element it points to be a single `ubyte`. Prog8 allows freely converting between both forms in an assignment.
- **pointer syntax**: The pointer declaration and dereference syntax is similar to Pascal's but Prog8 requires a double `^^` (because single `^` is already a taken operator). Note that `pointer[0]` is equivalent to `pointer^^`.
- **address-of operators**: The `&` operator returns the *untyped* address of its argument (a `uword`), whereas the `&&` operator returns a *typed pointer* to its argument. Note that `&&` is **NOT** the logical AND operator in Prog8 - that is written as `and`.
- **static memory allocation only**: Prog8 only has *static* memory allocation. The `memory()` builtin function returns the address of a statically reserved memory block (named with the given name). It is possible to statically initialize struct variables with the syntax `^^StructType pointer = ^^StructType:[1,2,3,4]`. The `^^StructType:` may be omitted from the initializer list if it is easy to infer it from the target variable type. The initializer list may be empty which means the struct instance is zeroed out *but only at program startup*. Real "dynamic" memory allocation is impossible, but it can be emulated with a simplistic "arena allocator" that just keeps track of a large `memory()` slab internally.

### Virtual Registers & Stack
- **Zeropage scratch variables:** The compiler provides these predefined zeropage scratch variables: `P8ZP_SCRATCH_B1` (byte), `P8ZP_SCRATCH_REG` (byte), `P8ZP_SCRATCH_W1` (word), `P8ZP_SCRATCH_W2` (word), `P8ZP_SCRATCH_PTR` (word). **No other zeropage locations can be used** - assembly routines must only use these predefined scratch variables. If additional storage is needed, define regular variables in the BSS section instead. You can also use the cx16 virtual registers (`cx16.r0`-`cx16.r15`) as temporary storage.
- the 16 'virtual registers' (cx16.r0 - cx16.r15) are available on ALL targets, not just CX16. They're fast 16-bit global variables but NOT preserved across subroutine calls. R12-R15 are especially dangerous: long operations may clobber them without warning. In IRQ handlers, save/restore with cx16.save_virtual_registers() / cx16.restore_virtual_registers(). You can give them descriptive names using aliases: `alias score = cx16.r7` but using regular variables is preferred.
- the CPU hardware stack can be manipulated via builtin functions: push(), pushw(), pushl(), pushf() and pop(), popw(), popl(), popf(). These can manually implement recursion if needed.

### Logic & Control Flow
- the syntax for boolean logical operators is 'and', 'or', 'xor', 'not'. Bitwise operators are '&', '|', '^', '~', and '<<','>>' for bit shifting left and right respectively. All operators are documented in docs/source/programming.rst
- CPU status flags can be tested with if_cs, if_cc, if_z, if_nz, etc. which compile to single 6502 branch instructions but require careful handling of flag state.
- use 'when' statements with choice blocks to avoid multiple 'if' statements.
- use 'repeat' statements instead of loops if the iteration count is not needed inside the loop body.
- use if-expressions instead of if-statements if the goal is to assign a single value to a variable based on a simple choice between two values.
- there is a 'defer' statement that can be used to defer the execution of a statement(s) until flow returns from the current scope.
- it is *allowed* to use 'goto' with labels, and jump lists, if needed; because that generates very optimal code

### Subroutines & Return Values
- **everything is public**: No private/public modifiers. All symbols accessible via fully qualified names from anywhere.
- **no function overloading**: Each subroutine must have a unique name (except for some builtin functions).
- subroutines can return 0, 1 or more return value(s). They can be assigned to multiple variables in a single multi-variable assignment: a,b,c = routine(). Values can be skipped using 'void'.
- **the `void` keyword has two forms:** (1) prefix form `void routine()` suppresses all return values from a subroutine call, (2) assignment form `a, void, c = routine()` skips specific return values in multi-return assignments.
- subroutines can be nested. Nested subroutines have direct access to all variables defined in their parent scope.

### Standard library
- **to discover what modules and routines are available in the standard library, FIRST consult docs/source/_static/symboldumps/** - there's a skeleton file per compilation target (e.g., skeletons-cx16.txt, skeletons-c64.txt) listing ALL available modules, subroutines, and builtin functions with their signatures.
- **when comparing routines between targets or checking if a routine exists**, always use the symboldump skeleton files first - they provide a quick overview without needing to read full source files. Only consult the actual `.p8` source files in `res/prog8lib/<target>/` when you need implementation details or the meaning of routines.
- **symboldump file structure**: Files start with compiler version info, then list **BUILTIN FUNCTIONS** (just names), followed by **LIBRARY MODULE NAME:** sections for each module. Within each module block `{...}`: variables/constants show `type  name` (with optional `@shared`/`@requirezp`/`@AY` etc. annotations), subroutines show `name  (params) -> returntype` (with optional `clobbers (X,Y)` for asm routines). Use grep/search to quickly find specific modules or routines.
- standard library source code is in the 'res/prog8lib' directory. See docs/source/libraries.rst for detailed descriptions of builtin functions and library routines.
- text output is done via the 'textio' module (txt.print, txt.chrout, etc.), math routines are in 'math', and string conversion routines are in 'conv'. **Note:** conv module uses `str_<type>` naming for number-to-string (e.g., `str_uword`, `str_long`), NOT `<type>2str`. String-to-number uses `str2<type>` (e.g., `str2uword`, `str2long`). **However, for printing numbers you don't need explicit conversion** - txt module has direct routines: `txt.print_b` (byte), `txt.print_ub` (ubyte), `txt.print_w` (word), `txt.print_uw` (uword), `txt.print_l` (long), `txt.print_bool` (bool).

### Syntax & Formatting
- **numeric literal syntax**: `$` prefix for hex (`$FF` not `0xFF`), `%` prefix for binary (`%1010` not `0b1010`). Underscores allowed for readability: `25_000_000`. No leading-zero octal notation. **No type suffixes**: long literals are just regular numbers (e.g., `12345678` not `12345678L`), the type is determined by context (variable type or cast).
- **for loop syntax**: `for i in 0 to 10 { ... }` (use downto when counting down) - NOT `for i = 0 to 10` or C-style `for(i=0; i<10; i++)`
- **semicolons start comments**: `; this is a comment` - they do NOT end statements. There is NO statement separator (unlike C/Java's `;`). One statement per line only. Multi-line comments use `/* ... */`.
- **trailing commas allowed**: `[1, 2, 3,]` is valid syntax.
- Prog8 source files are indented with 4 spaces, no tabs.
- assembly source files (*.asm) can be indented with either spaces or tabs
- **The assembly source code uses 64tass syntax, NOT ca65/cc65 or other assemblers.** Key 64tass syntax: `.proc`/`.pend` for procedures, `_label` for local labels, `.byte`/`.word`/`.dword` for data, `= ` for equates, zero-page variables defined with `=`. **Instructions like `rol`, `ror`, `asl`, `lsr` require an explicit operand** - use `rol a`, `ror a`, etc. for the accumulator, not just `rol` or `ror`.

## Other Key differences from other languages (C, Python, etc.)
- **no automatic type widening**: `byte*byte=byte` (may overflow!), `word*word=word`, etc. Explicitly cast operands: `word result = (bytevar as word) * 1000`. Hex literals with full width (e.g., `$0040`) also promote. Compiler does not warn by default.
- **no block scope**: `for`, `if/else` blocks do NOT introduce new scope. Only subroutines introduce scope. Variables declared anywhere in a subroutine are hoisted to the top.
- **qualified names from top level**: Must use full qualified names (e.g., `cx16.r0`), not relative imports.

## Project Module Descriptions
- `beanshell` - EXPERIMENTAL/UNFINISHED Contains BeanShell integration for scripting capabilities within the compiler
- `benchmark-c` - C implementations for performance comparison and benchmarking
- `benchmark-program` - Benchmark programs to test compiler output performance
- `codeCore` - Core code generation utilities and shared components
- `codeGenCpu6502` - 6502 CPU-specific code generator backend (generates assembly for 6502-based systems)
- `codeGenExperimental` - Experimental code generators that are under development
- `codeGenIntermediate` - Intermediate representation (IR) code generator
- `codeGenVirtual` - Virtual machine code generator backend
- `codeOptimizers` - Optimization passes for improving generated code efficiency
- `compiler` - Main compiler executable and top-level compiler logic
- `compilerAst` - complicated Abstract Syntax Tree (AST) where most optimizations also run on, is later transformed into the simpleAst
- `docs` - Documentation files for the Prog8 language and compiler
- `examples` - Example Prog8 programs demonstrating language features
- `intermediate` - Components related to the intermediate representation (IR) of Prog8 programs
- `languageServer` - Language Server Protocol implementation for IDE integration
- `parser` - ANTLR4 Parser implementation for the Prog8 language syntax
- `scripts` - Utility scripts for development, testing, and deployment
- `simpleAst` - Simplified AST that is used to run the code generator backends from
- `syntax-files` - Syntax definition files for editors and IDEs
- `virtualmachine` - Virtual machine implementation that can execute IR code

## Key Information
- never read the files and directories that are ignored via the .aiignore and .gitignore files
- never perform any git source control write/update/add/commit/branch operations. Read and status operations are allowed.
- **git log/history queries can be useful** for understanding when/why a feature was added or tracking down when a bug was introduced, but for locating code use grep_search or glob instead.
- Architecture decisions: separation of frontend/parser, IR intermediate representation, multiple backends

# Dev environment tips

## Commands to build the compiler
- use the system installed gradle command instead of the gradle wrapper.
- **IMPORTANT: Always run `gradle installdist installshadowdist` to rebuild the compiler after any code modifications.**
- `gradle build` - Full build of the compiler including running the full test suite
- `gradle clean` - Clean build artifacts
- `gradle compileKotlin` - Compile only the Kotlin source code
- `gradle installdist` - Create the compiler JARs and executable file
- `gradle installshadowdist` - Create the single "fat" compiler JAR and executable file

### Compilation Output Files
- `*.prg` - The final compiled program file for the target system (e.g., Commander X16)
- `*.asm` - Generated assembly code from the Prog8 source
- `*.list` - Generated full assembly listing file from the Prog8 source
- `*.p8ir` - Intermediate representation file, can be executed in the Virtual Machine
- `*.vice-mon-list` - Vice emulator monitor list file for debugging

## Commands to run tests
- `gradle test --tests "*TestName*"` - Run specific test classes
- `gradle build --refresh-dependencies` - Refresh dependencies during development
- Unit tests are written using KoTest
- Several modules in the project contain unit tests, but most of them live in the "compiler" module in the 'test' directory.
- **when writing TEST programs, always add these directives at the top: `%zeropage basicsafe` and `%option no_sysinit`** - this keeps zeropage usage safe and skips system initialization for faster, simpler test programs.

## Commands to run the Prog8 Compiler after building it
- the prog8c compiler executable can be found in the compiler/build/install/prog8c/bin folder (this is already added to the shell's path)
- the `-emu` switch can be used to directly execute the resulting program in an emulator after successful compilation.
- **the `-check` switch performs a quick syntax/semantic check only - it will NOT produce any output files (no .prg, .asm, etc.)**. Use it only for fast error checking during development.
- `prog8c -target cx16 input.p8` - Compile a Prog8 source file "input.p8" for the CommanderX16 target
- `prog8c -target cx16 -check input.p8` - Quickly check a Prog8 source file "input.p8" for compiler errors, no output binary is produced
- `prog8c -target cx16 -emu  input.p8` - Compile and execute a prog8 file in the CommanderX16 emulator
- `prog8c -target c64 -emu input.p8` - Compile and execute a prog8 file in the Commodore-64 emulator
- `prog8c -target virtual input.p8` - Compile a prog8 file for the IR/Virtual machine target
- `prog8c -target virtual -emu input.p8` - Compile and directly execute a prog8 file in the Virtual Machine
- `prog8c -vm input.p8ir` - Execute an existing prog8 program, compiled in IR form, in the Virtual Machine
- `x16emu -scale 2 -prg input.prg` - Just load an existing compiled program in the CommanderX16 emulator. Ignore any errors and warnings, because the emulator doesn't produce any output on STDOUT.
- `x64sc input.prg` - run an existing compiled program in the Commodore-64 emulator. Ignore any errors and warnings, because the emulator doesn't produce any output on STDOUT.

## TODO Items
The file docs/source/todo.rst contains a comprehensive list of things that still have to be fixed, implemented, or optimized.
