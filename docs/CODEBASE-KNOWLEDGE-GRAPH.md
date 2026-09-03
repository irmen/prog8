# Prog8 Compiler - Codebase Knowledge Graph

A structured map of the Prog8 compiler codebase: modules, their responsibilities, key entities, and the relationships between them. Updated from the codebase in September 2026.

---

## 1. Top-Level Architecture

The repository is a multi-module Gradle build. The **compiler** is the front-facing application (`prog8c`); the other modules are libraries it composes, most organized around a layered pipeline:

```
Source (.p8)  ->  parser  ->  compilerAst  ->  simpleAst  ->  codegen backends  ->  assembly/IR
                                   |                                      |
                              codeOptimizers                      intermediate <-> virtualmachine
```

The pipeline is linear and **frontend/backend separated**: the frontend produces an AST, the backends (6502/65C02, M68K, VM) consume it.

### Build entry points (`settings.gradle`)
| Module | Role |
|--------|------|
| `parser` | ANTLR4 grammar + generated parser |
| `codeCore` | Shared low-level core (no module deps) |
| `simpleAst` | Simplified AST + code generator interface |
| `intermediate` | IR representation + file I/O |
| `compilerAst` | Complex AST, semantic analysis |
| `codeOptimizers` | AST optimization passes |
| `virtualmachine` | VM that executes IR |
| `codeGenIntermediate` | IR code generator |
| `codeGenCpu6502` | 6502/65C02 assembly backend (legacy) |
| `codeGenNew6502` | New 6502 backend (reads IR) |
| `codeGenM68k` | Motorola 68k backend (reads IR) |
| `compiler` | Main CLI application |
| `languageServer` | LSP implementation |

Note: the AGENTS.md describes `codeGenVirtual` as a module, but it has no build file/classes; the `virtual` codegen is `VmCodeGen` inside `codeGenIntermediate`.

---

## 2. Module Dependency Graph

`A -> B` means "A depends on B". Assembled from each module's `build.gradle.kts`.

```
compiler
  -> codeCore, simpleAst, codeOptimizers, compilerAst
  -> codeGenCpu6502, codeGenNew6502, codeGenM68k, codeGenIntermediate, intermediate, virtualmachine
  -> clikt, kotlin-result, buildversion(gversion), ksim65(test)

parser               (no module deps; ANTLR4)
compilerAst          -> codeCore, parser
codeOptimizers       -> codeCore, compilerAst
simpleAst            -> codeCore
intermediate         -> codeCore
virtualmachine       -> codeCore, intermediate
codeGenIntermediate  -> codeCore, simpleAst, intermediate
codeGenCpu6502       -> codeCore, simpleAst
codeGenNew6502       -> codeCore, simpleAst, intermediate, codeGenIntermediate
codeGenM68k          -> codeCore, simpleAst, intermediate, codeGenIntermediate
languageServer       -> compilerAst, codeCore, parser
codeCore             (self-contained, kotlin-result only)

testFixtures: codeCore exposes java-test-fixtures (Dummies, ErrorReporterForTests)
```

**Dependency layering insight:** The AST/optimizer side (`parser`, `compilerAst`, `codeOptimizers`) and the codegen side (`simpleAst`, `intermediate`, `virtualmachine`, `codeGen*`) both depend only on `codeCore`. `codeGenNew6502` and `codeGenM68k` each additionally consume `intermediate` + `codeGenIntermediate` because they are **standalone binaries** (`prog8-newgen`, `prog8-m68kgen`) that read `.p8ir` files.

---

## 3. The Compilation Pipeline (end-to-end)

Orchestrated by `compiler/src/prog8/compiler/Compiler.kt` (`compileProgram()`, entry `compiler/src/prog8/CompilerMain.kt`).

```
CompilerMain.compileMain()
   -> CompilerCli (Clikt) parses args into CompilerArguments
   -> compileProgram(CompilerArguments)
      |
      |-- parseMainModule()                       Compiler.kt:468
      |     ModuleImporter.importMainModule()     imports/parses modules
      |     importImplicitLibraryModule()         syslib, prog8_math, prog8_lib (+verafx on cx16)
      |     determineCompilationOptions()          %output/%launcher/%zeropage/%zpreserved...
      |
      |-- processAst()                            Compiler.kt:612
      |     program.preprocessAst() -> AstPreprocessor
      |     checks: checkAsmSubRegisters, checkPrivateAccess, checkIdentifiers
      |     transforms: charLiteralsToUByte, constantFold, reorderStatements, desugaring,
      |                 changeNotExpressionAndIfComparisonExpr, addTypecasts, variousCleanups, checkValid
      |
      |-- optimizeAst()                            Compiler.kt:651
      |     UnusedCodeRemover (CodeOptimizers module)
      |     constantFold, simplifyExpressions, optimizeStatements, inlineSubroutines
      |     fixed-point loop 0..10000
      |
      |-- postprocessAst()                         Compiler.kt:707
      |     desugaring, addTypecasts, variousCleanups
      |     CallGraph.checkRecursiveCalls
      |     verifyFunctionArgTypes
      |     BeforeAsmAstChanger, BeforeAsmTypecastCleaner
      |
      |-- SimplifiedAstMaker.transform()          CompilerAst -> SimpleAst
      |     SymbolTableMaker.make()               -> SymbolTable
      |     postprocessSimplifiedAst()
      |     optimizeSimplifiedAst()               simpleAst/code/optimize/*
      |     SubtypeResolver.removeRedundantPointerCasts()
      |     (optional) profilingInstrumentation
      |     verifyFinalAstBeforeAsmGen()
      |     writeBankedCallsFile()
      |
      |-- createAssemblyAndAssemble()              Compiler.kt:745
            backend = pick from options.compTarget.cpu:
              6502/65C02 -> AsmGen6502  OR  New6502CodeGenerator (if -newcodegen)
              m68k       -> M68kCodeGenerator
              vm         -> VmCodeGen
            backend.generate(program, symbolTable, options, errors) -> IAssemblyProgram
            assembly.assemble()                     output .prg/.asm/.p8ir
```

**Target selection:** `compiler/src/prog8/CodeCore .../Compiler.kt:769-780`. The target CPU determines the backend. The custom target config file path is resolved in `codeCore` targets (`ConfigFileTarget.fromConfigFile`).

---

## 4. Module-level Entity Maps

### 4.1 `codeCore` (shared low-level core - the hub)
Package `prog8.code.*`, `prog8.code.target.*`.

| Entity | Kind | Purpose |
|--------|------|---------|
| `CompilationOptions` | class | Immutable compiler options (target, output, zeropage, romable, ...) |
| `DataTypes` | object | Prog8 data types (u8/u16/u32/str/... width, signed, floats) |
| `ICompilationTarget` | interface | Target abstraction: memory regions, floats, zeropage, launchEmulator |
| `CompilationTargets` (+ `getCompilationTargetByName`) | helpers | Registry of named targets |
| `C64Target`, `C128Target`, `PETTarget`, `Cx16Target`, `VMTarget`, `Amiga500Target`, `Qemu68kTarget`, `ConfigFileTarget` | classes | Concrete targets (via `target/*.kt`) |
| `*Zeropage` (C64/C128/CX16/PET/Configurable) | classes | Per-target zeropage layout (`target/zp/*`) |
| `encodings/*` (`Encoder`, PetsciiEncoding, IsoEncoding, Cp437Encoding, AtasciiEncoding, KatakanaEncoding, C64osEncoding) | classes | Text encoding/decoding per target |
| `MemoryRegions`, `NormalMemSizer`, `IMemSizer` | classes | Memory size/layout determination |
| `Mflpt5.kt` | class | 5-byte floating point format (target-dependent) |
| `AssemblyProgram6502`, `IAssemblyProgram` | classes | Assembly program container + `assemble()` |
| `ImportFileSystem`, `SourceCode` | classes | Filesystem import + source loading |
| `Operators`, `RegisterOrStatusflag`, `Enumerations`, `Conversions`, `Exceptions` | objects | Shared language primitives |
| `BuiltinFunctions.kt` | object | Shared builtin-function signature table |
| `CompilationOptions` included builtins for float conversion | - | target.float conversion helpers |

`codeCore` is depended on by **every** other module - it is the domain model / shared contract layer.

### 4.2 `parser` (ANTLR4)
- Grammar: Prog8 `.g4` file (in `parser/src`), generates `Prog8Parser`, `CommentHandlingTokenStream`.
- `-no-listener -visitor` generation: parse tree consumed by the AST builder visitor.
- No Kotlin logic beyond generated code.

### 4.3 `compilerAst` (complex AST + semantic analysis)
Package `prog8.ast.*`.
| Entity | Kind | Purpose |
|--------|------|---------|
| `Program` | class | Root AST node; holds `modules`, `toplevelModule`; hosts pipeline extension funcs (`preprocessAst`, `constantFold`, etc.) |
| `AstToplevel.kt` | file | Top-level nodes (Module, Block, Subroutine, Variable) |
| `AstExpressions.kt`, `AstStatements.kt` | files | Expression/statement AST node classes |
| `AstWalker` + `IAstVisitor` | classes | Generic AST traversal (visitor pattern) |
| `AstToSourceTextConverter` | class | AST -> Prog8 source text |
| `SymbolDumper` | class | `-dumpsymbols` output |
| `Antlr2KotlinVisitor` | class | ParseTree -> CompilerAst (from `antlr/`) |
| `CallGraph` | class | Call-graph analysis (recursion checks) - in `compiler/`? no, in `compilerAst` |
| `IBuiltinFunctions` | interface | Abstraction over builtin functions for optimization |
| `Errors`, `InferredTypes`, `Program`(helpers) | classes | Error + type-inference helpers |

### 4.4 `codeOptimizers` (AST-side optimization passes)
Package `prog8.optimizer.*`. Operate on the **compiler AST** during `optimizeAst()`.
| Entity | Purpose |
|--------|---------|
| `ConstantFoldingOptimizer` | Fold constant expressions |
| `ConstantIdentifierReplacer` | Replace const identifiers with values |
| `ConstExprEvaluator` | Evaluate constant expressions |
| `ExpressionSimplifier` | Algebraic simplification of expressions |
| `StatementOptimizer` | Rewrite/simplify statements |
| `Inliner` | Inline subroutine calls |
| `UnusedCodeRemover` | Dead-code elimination |
| `Extensions.kt` | Helpers |

### 4.5 `simpleAst` (simplified AST + codegen contract)
Package `prog8.code.*`, `prog8.code.ast.*`, `prog8.code.optimize.*`.
| Entity | Purpose |
|--------|---------|
| `PtProgram`, `PtExpressions`, `PtStatements`, `PtBase` | Simplified AST node base classes (`code/ast/*`) |
| `SymbolTable`, `SymbolTableMaker` | Symbol resolution table + builder |
| `SymbolPrefixer`, `BankedCallUtils` | Name mangling + banked call helpers |
| `ICodeGeneratorBackend` | **The backend contract**: `generate(program, symbolTable, options, errors): IAssemblyProgram?` |
| `optimize/*` (`Optimizer`, `ExpressionOptimizers`, `ComparisonOptimizers`, `ControlFlowOptimizers`, `BooleanOptimizers`, `MemoryOptimizers`, `VariableOptimizers`) | Simplified-AST optimization passes (run via `optimizeSimplifiedAst()`) |
| `AstPrinter`, `Verify` | Debug print + final validation |

### 4.6 `intermediate` (IR representation)
Package `prog8.intermediate.*`.
| Entity | Kind | Purpose |
|--------|------|---------|
| `IRProgram` | class | Top-level IR program (holds subroutines, chunks, instructions) |
| `IRInstructions.kt` | file | IR instruction/operand model (`@JvmInline` value classes) |
| `IRSymbolTable` | class | IR-side symbol table |
| `CallingConventionSlot` | class | Slot-based calling convention (registers/stack) |
| `IMSyscall` | enum | IR-level syscall identifiers |
| `IRFormat` | class | Serialization format constants |
| `IRFileReader`, `IRFileWriter` | classes | `.p8ir` file read/write |
| `VariableDump`, `Utils` | helpers | Variable dumping, utilities |

### 4.7 `codeGenIntermediate` (IR code generator - incl. virtual backend)
Package `prog8.codegen.intermediate.*`, `prog8.codegen.vm.*`.
| Entity | Purpose |
|--------|---------|
| `IRCodeGen` | Main IR generator (expression/builtin/assignment sub-generators) |
| `ExpressionGen`, `BuiltinFuncGen`, `AssignmentGen` | Sub-generators used by `IRCodeGen` |
| `RegisterPool`, `RegisterPacker` | Virtual register allocation |
| `IRPeepholeOptimizer`, `IRUnusedCodeRemover` | IR-level optimizations |
| `StConvert` | SimpleAst -> IR conversion helpers |
| `PreProcess`, `StructArrayFolding`, `SymbolPrefixer` | IR preprocessing |
| `VmCodeGen` (in `prog8/codegen/vm/`) | The **virtual target** backend + `VmAssemblyProgram` - produces `.p8ir` |

### 4.8 `codeGenCpu6502` (legacy 6502/65C02 backend)
Package `prog8.codegen.cpu6502.*`. Consumes SimpleAst directly.
| Entity | Purpose |
|--------|---------|
| `AsmGen6502` | Top-level 6502 assembly generator (`AsmGen.kt`, implements `IDebugInfoProvider`) |
| `ProgramAndVarsGen` | Program structure + variable section |
| `assignment/*` (`AsmAssignment`, `AssignmentAsmGen`, `AugmentableAssignmentAsmGen`, `BinaryOpAssignmentsGen`, `PointerAssignmentsGen`, `PrimitiveAssignmentsGen`, `TypeCastAssignmentsGen`, `AnyExprAsmGen`) | Assignment codegen |
| `ForLoopsAsmGen`, `IfElseAsmGen`, `IfExpressionAsmGen`, `FunctionCallAsmGen`, `BuiltinFunctionsAsmGen` | Statement/expression codegen |
| `VariableAllocator` | Variable-to-memory allocation |
| `AsmOptimizer`, `AsmsubHelpers` | Optimization + inline asm helpers |

### 4.9 `codeGenNew6502` (new IR-based 6502 backend - standalone)
Package `prog8.codegen.new6502.*`. Entry `Main.kt` -> `prog8-newgen`. Reads `.p8ir`.
| Entity | Purpose |
|--------|---------|
| `New6502CodeGenerator` | Orchestrates IR -> 6502 asm |
| `AsmGen`, `InstrArithmetic`, `InstrBitwise`, `InstrBranch`, `InstrControl`, `InstrLoadStore` | IR-opcode -> 6502 instruction emission |
| `ZeropageAllocator` | Zeropage allocation for IR regs |
| `PeepholeOptimizer` | 6502 peephole optimization |
| `StderrErrorReporter` | Error reporting for standalone mode |

### 4.10 `codeGenM68k` (M68K backend - standalone)
Package `prog8.codegen.m68k.*`. Entry `Main.kt` -> `prog8-m68kgen`. Reads `.p8ir` (both `amiga500` and `qemu68k` targets).
| Entity | Purpose |
|--------|---------|
| `M68kCodeGenerator` | Orchestrates IR -> M68K asm |
| `AsmGen`, `AssemblyProgramM68k`, `Instr*` (Arithmetic/Bitwise/Branch/Control/LoadStore/Syscall) | Opcode emission |
| `AsmOptimizer` | Peephole/selection optimization |

### 4.11 `virtualmachine` (IR executor)
Package `prog8.vm.*`.
| Entity | Purpose |
|--------|---------|
| `VirtualMachine` | Interprets IR instructions |
| `VmProgramLoader` | Loads `.p8ir` programs |
| `Memory`, `Registers`, `VmArithmetic`, `VmStackExtensions` | VM state + ALU |
| `SysCalls`, `VmSystemHandler` | IR syscall handling |
| `GraphicsWindow` | VM display output |
| `VmVariableAllocator` | Variable layout for VM |

### 4.12 `compiler` (the application)
Package `prog8.compiler.*` (entry `prog8/CompilerMain.kt`).
| Entity | Purpose |
|--------|---------|
| `CompilerMain` (`main`) | CLI + orchestration |
| `CompilerCli` | Clikt arg definitions |
| `compileProgram` | Main pipeline driver (see section 3) |
| `CompilerArguments`, `CompilationResult` | Data classes |
| `CompilerDaemon`, `DaemonProtocol` | Background persistent compiler process (Unix socket) + protocol |
| `ErrorReporter` | Error/warning output with colors |
| `ModuleImporter` | Module/import resolution + stdlib import |
| `BuiltinFunctions` | Builtin function signature table + const evaluators |
| `astprocessing/*` | All the `processAst`/`postprocessAst` transforms (AstPreprocessor, AstChecker, AstIdentifiersChecker, CodeDesugarer, DeferProcessor, SimplifiedAstMaker, SimplifiedAstPostprocess, TypecastsAdder, VariousCleanups, StatementReorderer, StructTypeResolver, SubtypeResolver, NotExpressionAndIfComparisonExprChanger, ListIterationHelper, ImplicitForIteratorDecls, ReflectionAstWalker, VerifyFunctionArgTypes, ... and more) |
| `simpleastprocessing/Instrumentation` | Profiling instrumentation (cx16 only) |

### 4.13 `languageServer` (LSP)
Package `prog8lsp.*`, entry `Main.kt` -> `prog8-language-server`, based on `eclipse lsp4j`.
| Entity | Purpose |
|--------|---------|
| `Prog8LanguageServer` | LSP server (LanguageServer, LanguageClientAware) |
| `Prog8TextDocumentService`, `Prog8WorkspaceService` | LSP services |
| `Prog8Parser` | Parsing wrapper (uses parser + compilerAst) |
| `SymbolLookup`, `SymbolExtractor` | Symbols/completion |
| `LspConversions`, `AsyncExecutor` | Conversions + async task runner |

---

## 5. Standard Library Map

Location: `compiler/res/prog8lib/`. These files are **embedded into the compiler JAR** during build; changes require `gradle installdist installshadowdist`.

### Layout
- **Implicit default modules** (always imported): `syslib`, `prog8_math`, `prog8_lib`, plus `verafx` on cx16 when `%option verafxmuls`.
- **Target-specific folders** override per target: `c64/`, `c128/`, `cx16/`, `pet32/`, `amiga500/`, `qemu68k/`, `virtual/`.
- **Shared** `.p8`/`.asm` files at top level: `prog8_lib`, `prog8_math`, `math`, `strings`, `conv`, `bcd`, `buffers`, `compression`, `coroutines`, `lists`, `sorting`, `wavfile`, plus `shared_*` suffix files reused across targets.

### Representative target module sets
- **cx16** (`cx16/`): `syslib`, `textio`, `graphics`, `gfx_hires`, `gfx_lores`, `monogfx`, `floats`, `diskio`, `bcd`, `bmx`, `psg`, `psg2`, `sprites`, `palette`, `adpcm`, `serial`, `emudbg`, `compression`, `buffers`, `verafx`, `prog8_lib`.
- **amiga500** (`amiga500/`): `syslib`, `exec`, `dos`, `graphics`, `intuition`, `audio`, `arexx`, `blitter`, `copper`, `custom`, `floats`, `ptplayer`, `icon`, `iffparse`, `timer`, `utility`, `textio`, `strings`, etc.
- **virtual** (`virtual/`): `syslib`, `textio`, `math`, `floats`, `bcd`, `compression`, `conv`, `coroutines`, `diskio`, `strings`, `sorting`, `monogfx`, etc.

### Usage / reference
- Quick search: `prog8c -libsearch "<regex>"`.
- Full dump: `prog8c -libdump <dir>`.
- Signatures reference: `docs/source/_static/symboldumps/skeletons-<target>.txt`.

---

## 6. Test Structure

Tests are KoTest (FunSpec) based, run with JUnit5, parallel execution (50% of cores), results in `compiler/build/reports/tests/test/index.html`.

### `compiler/test` (the bulk)
- `ast/` - parser, AST checks, const, identifiers, typecasts, simplified AST, private access, source code.
- `codegeneration/` - 6502 codegen behavior incl. `TestExecution6502` (uses the `ksim65` simulator via `simulate()` extension), structs/arrays, library, variables, loops.
- `optimizer/` - `TestExpressionSimplifier`.
- `vm/` - VM execution tests (`TestCompilerVirtual`, `TestRng`, `TestVariableStepForLoops`).
- `prog8tests/compiler/` + top-level - examples compilation, imports/includes, memory, optimization, helper `helpers/compileXyz`.

### Other module tests
- `codeCore/test` - data type, conversions, config target, zeropage freelist.
- `codeGenCpu6502/test` - asm optimizer, codegen.
- `codeGenNew6502/test` - asmsub returns, inline asm, register file, zeropage allocator.
- `codeGenM68k/test` - instruction selection optimizations.
- `codeGenIntermediate/test` - IR peephole, register packer, symbol prefixer, VM codegen.
- `simpleAst/test` - `SymbolTableConstruction`, `PtBuilders`, comparison optimizers.
- `intermediate/test` - IR file in/out, instructions.
- `languageServer/test` - LSP tests (`GotoDef`, `Rename`, `SignatureHelp`) via `LspTestHarness`.
- `virtualmachine/test` - memory, registers, VM.

---

## 7. Key Behavioral Notes for Contributors

- **SymbolTable cache**: `SymbolTable.flat` is cached; after AST modification call `symbolTable.resetCachedFlat()` (simpleAst `SymbolTable.kt`).
- **Virtual-target bug fixes**: only touch `codeGenIntermediate`, `intermediate`, `virtualmachine` (do not touch `compilerAst`/`simpleAst`/`codeGenCpu6502`).
- **zipping pipeline split**: AST optimizations (`codeOptimizers`) run before simplified AST; the simplified-AST optimizations (`simpleAst/code/optimize/*`) run after `SimplifiedAstMaker`. These are two distinct optimizer groups with the same goal at different AST levels.
- **`-noopt`** disables both optimization groups; use it to isolate optimizer bugs.
- **`-compareir` / `-vmtrace`** help debug IR/VM issues.

---

## 8. Source of truth / less reliable notes

- AGENTS.md describes a `codeGenVirtual` module, but the actual virtual backend is `VmCodeGen` in `codeGenIntermediate` with no standalone `codeGenVirtual` build file - it is referred to as a module historically but has no build.gradle.kts or source of its own.
- The `codeOptimizers` and `compilerAst` modules have no direct test source dirs of their own (their logic is exercised via `compiler/test`).
