# Agents.md

Context and instructions for AI Agents to work on this project.

## Project Overview
- This project is a compiler for the Prog8 programming language.
- Prog8 is a programming language primarily targeting 8-bit retro systems with the 6502 CPU, such as the Commodore 64, Commodore 128, and Commander X16.
- The compiler has a 6502 code generator backend, and an IR code generator.
- The IR code is meant to be used in a new machine specific code generator backend (primarily 6502 but maybe 68000 as well later)
- The compiler includes a simple 'virtual machine' that can execute the IR code directly via interpretation.
- Prog8 source files have .p8 extension – these are *not* LUA or PICO-8 source files in this case!
- Prog8 source files are a "module" that can contain one or more "blocks". They can also import other modules, from internal library files or from source files on the filesystem.
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
- Code generation – Simple AST → IR or 6502 assembly

**Understanding this order is important:** For example, `ConstantIdentifierReplacer` runs during `processAst()`, so by the time `AstChecker` runs at the end of that phase, identifier references have already been replaced with their actual values.

### Target Differences
- **CPU instruction set differences**: Only the CommanderX16 target (cx16) can use 65C02 instructions such as STZ. The other targets (C64, C128, PET32) can only use original 6502 instructions.


## DEBUGGING TIP: Use `-noopt` to isolate problems
When investigating a possible code generation problem (both IR and 6502), **FIRST try compiling the program with the `-noopt` switch** to disable most of the compiler optimizations.
- **Problem gone with `-noopt`**: Issue is in **optimization phases** (`optimizeAst()`, `UnusedCodeRemover`, `Inliner`, etc.)
- **Problem persists with `-noopt`**: Issue is in **parsing, semantic analysis, symboltable, or the regular code generation path**.

This way you can determine if the problem is caused by a faulty optimization step, or just occurs in the regular code generation path.

## DEBUGGING TIP: Use `-compareir` to see what changed
When investigating optimization-related issues or tracking regressions:
```bash
# Compile without optimizations (baseline)
prog8c -target virtual -noopt -out dir program.p8

# Compile with optimizations and compare
prog8c -target virtual -compareir dir/program_noopt.p8ir program.p8
```
**Output shows:**
- Instruction/chunk/register count changes with percentages
- First 10 actual instruction differences
- Helps identify which optimization transformed the code

## DEBUGGING TIP: Use `-vmtrace` to trace execution
When debugging VM execution or control flow issues:
```bash
prog8c -target virtual -vm program.p8ir -vmtrace
prog8c -target virtual -emu -vmtrace program.p8
```
**Output format:** `[chunkName:instructionIndex] instruction`
- Shows each executed IR instruction with location
- Useful for understanding control flow and finding where execution diverges
- **Only works on virtual target**

## Typical debugging workflow:
1. **Quick check:** `-check` for syntax errors
2. **Isolate:** `-noopt` to determine if problem is optimizer-related
3. **Compare:** `-compareir` to see what instructions changed
4. **Trace:** `-vmtrace` to watch actual execution flow
5. **Deep dive:** `-printast1` / `-printast2` for compiler internals

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
- DO NOT change indentation and formatting of lines that are not being modified. NEVER run formatters (black, ruff, prettier, etc.) after edits,
- .editorconfig handles basic formatting (indentation, line endings, whitespace)
- Make ONLY the requested changes, touch nothing else
- **NO EMOJI in user documentation**. Do not use emoji or decorative unicode symbols in documentation files. Functional unicode symbols are acceptable when they serve a clear purpose (e.g., → for arrows, ± for plus-minus, × for multiplication, ° for degrees). Avoid decorative emoji like ❌ ✅ ⚠️ 🎉 etc.

### Code Style Guidelines
**Minimal comments when making changes**: When modifying existing code, add only essential comments that explain *why* a change was made or document non-obvious behavior. **Do not add verbose comments** that restate what the code does; let the code speak for itself. Existing extensive comments should be preserved, but new changes should have minimal commentary.

## Prog8 language information

General Prog8 programming language instructions and feature hints can be found in the separate file [AGENTS-PROG8-LANG.md](AGENTS-PROG8-LANG.md). 
You MUST read that file as well to understand the language you are working with.

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

# Dev environment tips

## Development Workflows

**1. Testing your own Prog8 programs** (no compiler changes):
- No rebuild needed - just run `prog8c` directly
- Edit your `.p8` file → compile/run → check stdout output
- Example: `prog8c -target virtual -emu myprogram.p8`

**2. Testing compiler or standard library changes**:
- Rebuild required after every change to `.kt`, `.p8`, or `.asm` files in the compiler project
- Workflow: edit source → `gradle installdist installshadowdist` → test with `prog8c`
- Example: fix bug in `CodeGen6502.kt` → rebuild → `prog8c -target cx16 test.p8`

## Building and Installing the Compiler
- use the system installed gradle command instead of the gradle wrapper.

### Quick compile check (NO tests)
- **After changing compiler Kotlin source (.kt files)**: Use `gradle :compiler:compileKotlin` to quickly check for syntax/compile errors
- This compiles the compiler but **skips running tests**, this is much faster than `gradle build`
- **Note:** This does NOT install the compiler - use `gradle installdist installshadowdist` after to actually use your changes

### When you MUST rebuild AND reinstall the compiler

**After ANY change to:**
1. **Kotlin compiler source code** (`.kt` files in any module, or the `.g4` grammar file)
2. **Standard library Prog8 files** (`compiler/res/prog8lib/**/*.p8`)
3. **Standard library assembly files** (`compiler/res/prog8lib/**/*.asm`)

**Run:** `gradle installdist installshadowdist`

**Why:** The standard library files (.p8 and .asm) are embedded into the compiler JAR during the build. Changes to these files are NOT picked up by simply running `prog8c` - you MUST rebuild and reinstall for your changes to take effect.

**Without this step, your changes will NOT be reflected when running `prog8c`!**
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

## Using the Compiler (prog8c)
- the prog8c compiler executable can be found in the compiler/build/install/prog8c/bin folder (this is already added to the shell's path)
- **the `-check` switch performs a quick syntax/semantic check only; it will NOT produce any output files (no .prg, .asm, etc.)**. Use it only for fast error checking during development.
- **the `-noopt` switch DISABLES all optimizations** - useful for debugging to determine if a problem is caused by the optimizer. **Optimizations are ENABLED by default** (no flag needed).
- **prog8c uses single-dash command line options** (e.g., `-target`, `-noopt`, `-check`), NOT double-dash (`--target` is invalid).
- **the `-printast1` switch prints out the internal Compiler AST** after parsing and semantic analysis.
- **the `-printast2` switch prints out the optimized Simple AST** just before it goes to the code generator. This is useful for debugging optimizer issues.
- **the `-out outdir` switch sets an alternative output directory** for compiled files (.prg, .asm, .list, etc.). **By default, output files are written to the same directory as the source file**.

### Compilation Output Files
- `*.prg` - The final compiled program file for the target system (e.g., Commander X16)
- `*.asm` - Generated assembly code from the Prog8 source
- `*.list` - Generated full assembly listing file from the Prog8 source
- `*.p8ir` - Intermediate representation file, can be executed in the Virtual Machine
- `*.vice-mon-list` - Vice emulator monitor list file for debugging

### Execution Examples
- `prog8c -target targetname input.p8` - Compile a Prog8 source file "input.p8" for the given target (cx16, c64, pet32, c128, virtual)
- `prog8c -target targetname -emu input.p8` - Compile and execute a prog8 file in the emulator for the given target (cx16, c64, pet32, c128, virtual)
- `prog8c -vm input.p8ir` - Execute an existing prog8 program, compiled in IR form, in the Virtual Machine

## Testing and Verification

### Automated Tests (gradle)
- `gradle build` - Full build including all tests (about 50s)
- `gradle :compiler:test` - Run only compiler tests (faster)
- `gradle :compiler:test --tests "prog8tests.compiler.TestOptimization"` - Run specific test class

**Language Server test logging:**
The languageServer tests have two verbosity levels:
```bash
# Normal mode - only shows test failures (silent on success)
gradle :languageServer:test

# Quiet mode - only shows failures (same as normal for tests)
gradle :languageServer:test --quiet

# Verbose mode - shows detailed LSP operation logs
gradle :languageServer:test -Dlsp.verbose=true
```

Note: By default, Gradle only shows failed tests. Passed and skipped tests are silent.

**⚠️ CRITICAL: Test Filtering Patterns - Read This First!**

Gradle's `--tests` filter has **strict rules** that are easy to get wrong:

| Pattern | Example | Works?        |
|---------|---------|---------------|
| **Full class name** | `--tests "prog8tests.compiler.TestOptimization"` | **USE THIS**  | 
| Wildcard at END | `--tests "prog8tests.compiler.Test*"` | Yes           |
| Wildcard on package | `--tests "prog8tests.compiler.*"` | Yes           |
| **Wildcard at START** | `--tests "*TestOptimization"` | FAILS         |
| Test description | `--tests "*Optimization*inline*"` | FAILS         |

**Why the restrictions?** Gradle's `--tests` filter matches **fully qualified class names only**. Wildcards work as **suffixes** (e.g., `Test*`) but NOT as prefixes (e.g., `*Test`). KoTest test names like `"inline multi-value void"` are _descriptions_, not method names, so you cannot filter by them.

**To find failing tests after a run:**
```bash
# Check XML results (reliable)
grep "<failure" compiler/build/test-results/test/*.xml

# Or check HTML report (most reliable)
cat compiler/build/reports/tests/test/index.html

# For test compilation errors
gradle :compiler:compileTestKotlin --info 2>&1 | grep "^e:"
```

**Additional notes:**
- Unit tests use KoTest (FunSpec style)
- Tests are in the "compiler" module's `test` directory (and some other modules)
- Test config is in root `build.gradle.kts`; tests run in parallel
- **When writing test programs**, add at the top: `%zeropage basicsafe` and `%option no_sysinit`
- When a test fails, the output shows "There were failing tests. See the report at:" - **read that HTML report**

### Manual Verification & Emulators

**Testing tip**: When writing and testing Prog8 programs, **use the `virtual` target** (e.g., `prog8c -target virtual -emu input.p8` or `prog8c -vm input.p8ir`). This is the preferred way to test because the virtual target can easily write output to stdout, making it simple to verify program behavior and check results.

**CX16 output verification**: Use `x16emu -echo iso -run -prg input.prg` to echo screen output to stdout **and auto-start the program**. The `-run` flag is **critical**: without it, the program loads but doesn't execute, so you'll see no output. Pipe through `strings` or `grep` to filter: `x16emu -echo iso -run -prg input.prg 2>&1 | grep -E "(PASS|FAIL)"`.
**IMPORTANT**: Always add `%encoding iso` at the top of your source file and call `txt.iso()` in `start()`. This prevents PETSCII→ISO charset translation errors that garble uppercase/special characters and make output unreadable:
```prog8
%encoding iso
%import textio
main { sub start() { txt.iso(); txt.print("PASS\n") } }
```
**IMPORTANT: Always use `sys.poweroff_system()` to exit the CX16 emulator cleanly!** Add `sys.poweroff_system()` at the end of your main program block - this exits x16emu automatically in most cases.
**Note:** The `sys` module is always available, there is no need to import it ever.

**Commodore 64 (x64sc)**: `x64sc input.prg` - run an existing compiled program in the Commodore-64 emulator. Ignore any errors and warnings, because the emulator doesn't produce any output on STDOUT.

## Git Operations for File Moves/Deletes

**When renaming or moving git-tracked files, ALWAYS use `git mv`:**
```bash
# ✅ CORRECT - preserves git history
git mv old/path/File.kt new/path/File.kt

# ❌ WRONG - git sees this as delete + add (loses history)
mv old/path/File.kt new/path/File.kt
```

**When deleting git-tracked files, ALWAYS use `git rm`:**
```bash
# ✅ CORRECT - properly stages the deletion
git rm path/to/File.kt

# ❌ WRONG - git sees this as unstaged deletion
rm path/to/File.kt
```

**Why this matters:** `git mv` and `git rm` properly stage the changes and preserve file history. Plain `mv`/`rm` requires git to detect renames heuristically, which may not always work correctly.

## TODO Items
The file `docs/source/todo.rst` contains a comprehensive list of things that still have to be fixed, implemented, or optimized. **Use this to understand what features are NOT yet available** in the compiler or Prog8 language: if a user asks for something that's on the TODO list, you'll know it's not implemented yet and can explain the limitation.
