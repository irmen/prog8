# Plan: `-o <outputfile>` option

Implements gillham's request from [GitHub issue #238](https://github.com/irmen/prog8/issues/238):
a compiler option to specify the exact name/path of the final program artifact, with all
other output files placed alongside it.

Tracked in `docs/source/todo.rst:16`.

## Decided semantics

| Case | Behaviour |
|---|---|
| `-o build/game.pgz` on a `%output PRG` target | final artifact is exactly `build/game.pgz` (verbatim, no extension check, no warning) |
| `build/game.asm`, `.list`, `.vice-mon-list`, `.p8ir`, `.bankedcalls`, `.o`, `.link.ld` | same directory `build/`, same stem `game` |
| `-o` + `-out dir` | hard error, message telling the user to pick one |
| `-o` + more than one `.p8` positional arg | hard error |
| `-o` on `virtual` target | `-o` names the `.p8ir` file itself (that *is* the program artifact there) |
| `-o` with no extension (`-o game`) | file is literally `game`; aux files are `game.asm` etc. |
| `-o` + `-noasm` / `-check` / `-dumpsymbols` / `-gendoc` | no artifact is produced at all; `-o` is silently ignored, as today |
| no `-o` | byte-for-byte identical to today (all defaults are `null`); the **legacy stem is used verbatim**, never re-derived by `outputStemOf` |

The `-o` name is verbatim: `%output` / `OutputType` still decides the *content* format
(`--cbm-prg`, `--atari-xex`, `--nostart`, `elf32m68k`, ...), only the file name changes.
A `.pgz` name on a `%output RAW` target therefore yields a *headerless* file - the user
asked for that name, the compiler does exactly that.

## Core mechanism

1. **`CompilationOptions.outputFile: Path? = null`** (`codeCore/src/prog8/code/core/CompilationOptions.kt:35` area)
   - the user's exact artifact path; `null` = legacy naming.
   - Add it to the primary constructor **only**. `Builder.build()`
     (`CompilationOptions.kt:111-119`) is a positional call that already omits the
     defaulted `privateSymbols: Boolean = false` (`:39`), so a defaulted parameter does
     **not** have to be added there. Do not touch `build()` - inserting a positional
     argument would break the `privateSymbols` omission.
   - There is no `with` block in this file; the builder is a `Builder` class with
     `= apply { ... }` setters (e.g. `:105`).
   - Follow the `privateSymbols` precedent for setting it after `build()`:
     `.build().apply { this.outputFile = ... }` (see `compiler/src/prog8/compiler/Compiler.kt:609`).
   - `determineCompilationOptions` (`compiler/src/prog8/compiler/Compiler.kt:527`) does not
     set it - it defaults to `null`.

2. **Propagate from args to options** in the `with(compilationOptions)` block
   (`Compiler.kt:129-149`, `outputDir` is assigned at `:147`):
   ```kotlin
   outputDir = args.outputFile?.parent ?: args.outputDir.normalize()
   outputFile = args.outputFile
   ```
   - Re-pointing `outputDir` to the `-o` parent makes the `.binary` paths in
     `codeGenCpu6502/src/prog8/codegen/cpu6502/AsmGen.kt:2084-2097` correct: 64tass resolves
     `.binary` relative to the **including `.asm` file**, and the `.asm` is written to
     `options.outputDir` (`AsmGen.kt:113`).
   - **Invariant to state in code**: `OutputFiles.asm()` must resolve to the same directory
     as `options.outputDir`. If those two ever diverge, `%asmbinary` silently breaks at
     64tass time.
   - Note `outputDir` has non-artifact uses that this re-pointing also affects, and which
     the later steps handle: `IRFileWriter.kt:297` (serialises it into the `.p8ir`, so the
     standalone codegen tools write their `.asm` there), `CompilerMain.kt:151-152`
     (directory creation), `CompilerDaemon.kt:147-158` (response file list).

3. **New helper file `codeCore/src/prog8/code/core/OutputFiles.kt`** - single place that knows artifact naming:
   - `outputStemOf(name: Path): String` - `"game.pgz"` -> `game`, `"game"` -> `game`,
     `".pgz"` -> `.pgz` (a dot at index 0 is not an extension). **Applied only to
     `options.outputFile`.** `program.name` is `filepath.nameWithoutExtension`
     (`Compiler.kt:478`), so a source named `my.game.p8` has the stem `my.game` today; running
     `outputStemOf` over it would rename every artifact and break the "identical to today"
     guarantee.
   - `fun CompilationOptions.programFile(defaultName: String): Path` - `outputFile` if set, else
     legacy `outputDir.resolve(...)` per `options.output` (`Enumerations.kt:168-175`):
     `PRG` -> `.prg`, `XEX` -> `.xex`, `RAW` -> `.bin`, `LIBRARY` -> `.bin`, `ELF` -> `.elf`,
     `AMIGAHUNK` -> no extension (just the name).
     **Must special-case `VMTarget`**: `VMTarget.kt:21` declares `defaultOutputType = OutputType.PRG`
     but the artifact is `IRFileWriter.kt:46`'s `.p8ir`.
   - `class OutputFiles(val programFile: Path, val stem: String, val dir: Path)` with
     `asm()`, `list()`, `viceMonList()`, `binFile()`, `ir()`, `bankedCalls()`, `obj()`,
     `linkScript()`.
     - `viceMonList()` is `<dir>/<stem>.vice-mon-list` - the **stem**, never
       `programFile + ".vice-mon-list"`.
     - `binFile()` is needed for the stale-`.bin` cleanup at `AssemblyProgramM68k.kt:96`,
       which is keyed on the aux stem, not on the artifact.
   - Factory `OutputFiles.for(options, defaultName)`; legacy stem = `defaultName` verbatim,
     `-o` stem = `outputStemOf(outputFile)`.
   - **Must handle `programFile.parent == null`** (bare filename like `game.pgz`): fall back to
     `outputDir`. (`Path("").resolve("x") == Path("x")`, so both spellings agree here.)
   - **Move `C64Target.viceMonListName` (`C64Target.kt:26`) into `OutputFiles`** and delete it.
     It is a C64-scoped companion helper currently called from 4 targets plus
     `AssemblyProgram6502.kt:27`.

4. **`.p8ir` round-trip**: emit `outputFile=<absolute>` **only when non-null**, next to
   `outputDir=` in `intermediate/src/prog8/intermediate/IRFileWriter.kt:297`, parse in
   `IRFileReader.kt:172`, feed into the builder at `:191`.
   - Must be `.absolute()`, exactly like `outputDir` at `:297`, or the standalone codegen tools
     resolve it against their own cwd.
   - Emitting unconditionally would break *every* `.p8ir` for older readers with
     `illegal OPTION outputFile` (`IRFileReader.kt:176`), even for users who never pass `-o`.
     Emitting only when set keeps the default path byte-identical.
   - Blast radius of that error is wider than the two named tools: `prog8c -vm new.p8ir`
     (`VMTarget.kt:83-86` -> `VirtualMachine.kt:3165`) and `-compareir`
     (`CompilerMain.kt:527`) also read the file, so a new-compiler / old-binary combination
     would fail. Acceptable; tools ship together.
   - `IR_FORMAT_VERSION` (`IRFormat.kt:12`, currently `4`) needs **no** bump. It is validated
     with strict string equality at `IRFileReader.kt:84-85` and is asserted by name in
     `intermediate/test/TestIRFileInOut.kt:41` plus 7 further hand-written `IRFORMAT="4"`
     fixtures - a bump means 10 coordinated edits for no benefit.
   - Note `IRFileWriter` already has an unused `outfileOverride: Path?` parameter
     (`IRFileWriter.kt:45`) that all three production call sites pass as `null`
     (`VmCodeGen.kt:58`, `New6502CodeGenerator.kt:37`, `M68kCodeGenerator.kt:34`). Prefer
     threading `OutputFiles.for(...)` through that parameter over adding resolution logic
     inside `IRFileWriter`.
   - `prog8-newgen` / `prog8-m68kgen` (`codeGenNew6502/src/.../Main.kt`,
     `codeGenM68k/src/.../Main.kt`) only call `generate()`, never `assemble()`, so `-o` affects
     only their `.asm` name. Note the *in-process* `New6502CodeGenerator.kt:44` /
     `M68kCodeGenerator.kt:40` **do** return the assembly wrappers, so `-newcodegen` on cx16
     also goes through step 7.

## Ordered steps

5. **CLI** (`compiler/src/prog8/CompilerMain.kt`):
   - New option `val outputFile by option("-o", "--output", help = "name of the output program file (and directory for the other output files)")`
     declared between `-noopt` (`:90`) and `-out` (`:91`), matching the existing
     `option("-x", "--x")` convention and `docs/source/compiling.rst:163-164`.
   - Validation following the existing pattern (`presenter.printErrorLine(...)` + `return false`,
     as at `:154-163`) - there is no separate validate step, checks are inline in `compileMain`:
     - both `-o` and `-out` given -> error
     - `-o` with >1 source file -> error
     - `-o` pointing at an existing directory -> error
   - Directory creation (`:151-152` is `val outputPath = pathFrom(outputDir); outputPath.createDirectories()`):
     when `-o` is given, create `outputFilePath.parent` if non-null; otherwise keep `:151-152`
     as-is. `Path(".")` creation is a no-op, so `-o` without `-out` is naturally safe.
   - `pathFrom` (`:54`) does no tilde expansion. Decide deliberately whether `-o` should;
     `-srcdirs` (`:165`) and `-target` (`:174`) do, `-out` does not. Either way, document it.
   - **Not covered:** `-noasm` / `-check` / `-dumpsymbols` / `-gendoc` produce no artifact and
     silently ignore `-o`. This is documented, not an error.

6. **`CompilerArguments.outputFile: Path? = null`** (`compiler/src/prog8/compiler/Compiler.kt:70`,
   right after `outputDir`). `CompilerArguments` is a plain `class`, not a `data class`.
   The default keeps every existing call site and test compiling. **Four** production sites:
   - `CompilerMain.kt:239` watch mode (`outputPath` arg at `:266`)
   - `CompilerMain.kt:327` daemon client (`outputPath` arg at `:354`)
   - `CompilerMain.kt:395` normal (`outputPath` arg at `:422`)
   - `CompilerDaemon.kt:195` `toCompilerArguments` (all-named args, `outputDir = resolvedOutputDir` at `:222`)

   All sites pass everything from `cwd` onward by name and the preceding parameters positionally,
   so inserting a parameter after `outputDir` is source-compatible - verify per site.

7. **`codeCore/src/prog8/code/assembly/AssemblyProgram6502.kt`** (package `prog8.code.assembly`,
   not `prog8.code.core.assembly`):
   - The six `outputDir.resolve(...)` fields (`:23-28`) are **constructor initialisers**, and the
     constructor takes `outputDir: Path` (`:16`) - `assemble()` (`:30`) receives `options` only
     afterwards. The constructor must change: replace the `outputDir` parameter with
     `CompilationOptions` (or add it), so the three construction sites still compile:
     `codeGenCpu6502/.../AsmGen.kt:133`, `codeGenNew6502/.../New6502CodeGenerator.kt:44`,
     `codeGenM68k/.../M68kCodeGenerator.kt:40`.
   - Build `val outs = OutputFiles.for(options, name)` at the top of `assemble()` and drop the fields.
   - All four branches (PRG `:43`, XEX `:65`, RAW `:86`, LIBRARY `:106`) pass `outs.programFile`
     to `addRemainingOptions` (`:38`) instead of `prgFile`/`xexFile`/`binFile`;
     `--labels=` (`:47/:69/:89/:110`) uses `outs.viceMonList()`, `--list=` (`:58/:80/:100/:121`)
     uses `outs.list()`.
   - `removeGeneratedLabelsFromMonlist()` (`:168`) and `generateBreakpointList()` (`:179`) take
     **no parameters** and read the `viceMonListFile` *field* - i.e. they duplicate the path
     independently of the `--labels=` string. They must receive the same `OutputFiles` instance
     used to build `--labels=`, or `readLines()` at `:170` throws `NoSuchFileException`.

8. **`codeGenM68k/.../AssemblyProgramM68k.kt`**: same treatment for `:18` (asm), `:20`
   `elfFile()`, `:75` obj, `:76` / `:120` list, `:96` stale-`.bin` cleanup, `:99` link script,
   `:119` extension-less hunk exe. `elfFile()` has exactly one caller (`:100`) and becomes a
   private accessor over `OutputFiles`. Constructor change needed here too
   (`M68kCodeGenerator.kt:40`).

9. **`.asm` writers** (3 sites) and **`.p8ir` writer**: `codeGenCpu6502/.../AsmGen.kt:113`,
   `codeGenNew6502/.../AsmGen.kt:120`, `codeGenM68k/.../AsmGen.kt:580`,
   `intermediate/.../IRFileWriter.kt:46` - resolve via `OutputFiles` instead of
   `"${program.name}.asm"` / `"${irProgram.name}.p8ir"`. The `.p8ir` name is written from three
   places (`VmCodeGen.kt:58`, `New6502CodeGenerator.kt:37`, `M68kCodeGenerator.kt:34`) - all three
   go through `IRFileWriter.kt:46`.

10. **`.bankedcalls`**: `simpleAst/.../BankedCallUtils.kt:33` uses the aux stem.
    Consequence to accept (not a bug): the "call-site IDs changed" read-back at `:36-57` stops
    finding the old file when the output name changes, exactly as it already does with `-out`.

11. **Emulator launchers - remove the stem hack.** Today `CompilerMain.kt:447-448` builds
    `outputPath.resolve(name)` and `removeSuffix(".prg")` (a no-op there, since `compilerAst.name`
    has no extension), and each launcher re-appends an extension or probes siblings.
    Change `ICompilationTarget.launchEmulator(selectedEmulator, programFile, quiet)`
    (`ICompilationTarget.kt:96`) to receive the **actual artifact path with extension**, and add
    KDoc stating the contract (it is a public interface in the published `codeCore` module).
    - `C64Target.kt:71-73`, `C128Target.kt:66-68`, `PETTarget.kt:65-67`: `-autostart <programFile>`,
      `-moncommands <programFile.viceMonListSibling()>`
    - `Cx16Target.kt:68` (box16 `-sym <monlist>`), `:79` (`-prg <programFile>`)
    - `Amiga500Target.kt:108`, `Amiga1200Target.kt:68`: pass the file as-is (drop the no-op
      `resolveSibling("${fileName}")`)
    - `Qemu68kTarget.kt:64-65`: use the artifact directly instead of probing siblings. The `.bin`
      probe is already dead - `AssemblyProgramM68k.kt:96` deletes `$name.bin` and nothing produces
      a `.bin` for qemu68k (m68k only ever yields `.elf`, `.o`, or the extension-less hunk exe).
      Keep an error message if the artifact does not exist.
    - `VMTarget.kt:74-76`: `launchEmulator` delegates to `launchEmulatorWithTrace` (`:78`), which
      is a **public method not on the interface**, with three call sites: `CompilerMain.kt:374`,
      `:456`, and `runVm` at `:513`. Move the `.p8ir` fallback **into `runVm`** and make
      `launchEmulatorWithTrace` exact-path only. Reason: the fallback is currently shared by the
      `-vm` flow (which needs it) and the `-emu` flow (which must not). Without this move,
      `-o build/game -target virtual -emu` (extension-less `-o`) appends `.p8ir`, does not find
      it, and throws an uncaught `NoSuchFileException` - a regression this plan would introduce.
    - `viceMonListSibling()` must call the **same** `outputStemOf`, not
      `Path.nameWithoutExtension` - the latter returns `""` for `.pgz` where `outputStemOf`
      returns `.pgz`.
    - Callers: `CompilerMain.kt:447-448` and `:368-369` (daemon) both pass the real path via
      `OutputFiles.for(...)`. Steps 11 and 13 must land in the same commit: today the daemon
      returns `[.prg, .asm, .p8ir]` and `CompilerMain.kt:368` takes `.first()`.
    - Side effect worth recording: this also fixes `-emu`, which is **already broken today** for
      `XEX`, `RAW` and `LIBRARY` - the launchers hardcode `.prg` regardless of `OutputType`.
    - `ConfigFileTarget.launchEmulator` throws (`ConfigFileTarget.kt:185-187`) - unchanged, so
      `-o` + custom target + `-emu` still dies with a raw exception.

12. **`-compareir`**: `CompilerMain.kt:451` use `OutputFiles.for(...)`, taking the options from
    `compilationResult.compilationOptions` - **not** from the CLI option, or `-o` + `-compareir`
    compares the file against itself. Note `:451` currently uses the CLI-level `outputPath`, as
    does `:447-448`.

13. **Daemon**: add `outputFile: String?` to `DaemonRequest` (`DaemonProtocol.kt:32`), encode with
    `propOpt("outputFile", req.outputFile)` after `outputDir` at `:80` (`propOpt` emits nothing when
    null; the `append("null"); setLength(length-5)` placeholder hack at `:83-84` handles the
    trailing comma), decode with `map["outputFile"] as? String` after `:147`. Backward/forward
    compatible. Populate from `compilerArgs.outputFile` at `CompilerMain.kt:867`.
    Plumb into `toCompilerArguments` (`CompilerDaemon.kt:186-227`), resolving relative to the
    client cwd exactly like `outputDir` (`:189-194`).
    Replace the hand-rolled name guessing at `CompilerDaemon.kt:147-158` with
    `OutputFiles.for(result.compilationOptions, result.compilerAst.name)` - **program file first**
    (current order is `[.prg, .asm, .p8ir]`; `CompilerMain.kt:368` uses `.first()`).
    This also fixes its existing inaccuracies (it ignores `.xex`, `.bin`, `.elf`, `.list`,
    `.vice-mon-list` and the extension-less Amiga hunk exe).
    **Preserve the `writeAssembly` guard**: today's `.p8ir` entry is gated on
    `!request.writeAssembly || target == VMTarget`. An unconditional list would make `-noasm`
    report a nonexistent artifact that `.first()` then feeds to the emulator.

14. **`prog8-m68kgen` result handling**: `codeGenM68k/src/.../Main.kt:26-27` ignores
    `generate()`'s return value and always prints "Generated assembly: ...". With `-o` pointing
    into a directory that may not exist, this silently lies. Also fix the hardcoded
    `"${program.name}.asm"` in that message.

## Tests

New `compiler/test/TestOutputFileName.kt` (directly in `compiler/test/`, `package prog8tests.compiler` -
the `compiler/test/prog8tests/compiler/` folder contains a single unrelated file), plus additions to
`compiler/test/TestDaemonProtocol.kt`.

- `OutputFiles` unit tests (pure, no compilation):
  - `outputStemOf`: `game.pgz` -> `game`, `game` -> `game`, `.pgz` -> `.pgz`, `my.game` -> `my`.
  - `programFile()` per `OutputType`: `PRG`/`.prg`, `XEX`/`.xex`, `RAW`/`.bin`,
    `LIBRARY`/`.bin`, `ELF`/`.elf`, `AMIGAHUNK`/no extension, `VMTarget`/`.p8ir`.
  - `OutputFiles.for(options, "prog")` with `outputFile = tmp/out/custom.pgz`: every accessor
    resolves under `tmp/out` with stem `custom`; `viceMonList()` is `custom.vice-mon-list`
    (never `custom.pgz.vice-mon-list`).
  - **Legacy-stem regression guard**: with `outputFile = null`, a default name containing dots
    (`my.game`) yields aux files `my.game.asm` etc., unchanged.
  - bare `outputFile` (`game.pgz`, `parent == null`) resolves relative to `outputDir`.
- Compilation semantics via a **local** `compileTheThing`-style helper - `compileText`
  (`compiler/test/helpers/compileXyz.kt:63`) and `compileFile` (`:12`) have no `outputFile`
  parameter and no `CompilationOptions` escape hatch, so either add `outputFile: Path? = null`
  to both (they have ~90 call sites) or copy the pattern from
  `compiler/test/TestCompilerOnExamples.kt:55-83`. The test must create the parent directory
  itself: `compileProgram` never calls `createDirectories()` (only `CompilerMain.kt:152` does).
  Assert `out/custom.asm` exists, `out/on_the_fly_test_*.asm` does not, and that
  `result.compilationOptions.outputFile` survives into `CompilationResult`.
- End-to-end artifact name: cx16 with `assemble = true` - 64tass must be on `PATH`
  (installed in CI, `.github/workflows/all-ci.yml:15-19`; there is no skip mechanism, a missing
  64tass turns the test red, same as for every existing cx16 test). Precedent:
  `compiler/test/codegeneration/TestVariableStepForLoops6502.kt:122` (NOT `TestArraysOfStructs.kt`,
  whose `assemble = true` calls are all `VMTarget` and invoke no external assembler).
  Assert `out/custom.pgz` exists with a 2-byte CBM load header and that no source-named `.prg`
  was produced. m68k variant only with `writeAssembly=true, assemble=false` (vasm/vlink are **not**
  in CI).
- `%asmbinary` under `-o`: compile a program with `%asmbinary` and `-o <fresh dir>/x.prg`, with
  `assemble = true`, and assert success. This is the only test that pins the
  `.binary` / `.asm` co-location invariant.
- `virtual` target: `outputFile` = `out/ir.p8ir`, assert that file exists and the VM can run it.
- Daemon protocol encode/decode round-trip of the new field: update test 1
  (`TestDaemonProtocol.kt:11-75`, which enumerates every field) and assert `decoded.outputFile
  shouldBe null` in test 2 (`:77-117`).
- Regression guard: `compiler/test/TestCompilerOnExamples.kt:109` `verifyOutputFileSize` builds
  paths by hand - switch it to `OutputFiles.for(...)` so it keeps working, and expect **no**
  size changes (all default paths are unchanged). Note these size checks are documented as
  fragile (`TestCompilerOnExamples.kt:21-33`).
- **Not tested**: CLI option validation (`-o`+`-out`, `-o`+multiple files, `-o`+directory).
  `compileMain` and `CompilerCli` are both `private` and no test in the repo invokes the CLI.
  Accepted gap.

## Risks / known consequences

- Step 11 touches 9 launcher implementations and changes a public interface's parameter meaning;
  it is the only genuinely invasive part. Required for `-o` + `-emu` to work, and it deletes the
  fragile `removeSuffix(".prg")` idiom. Steps 11 + 13 land in the same commit; everything before
  that is a separate commit.
- `%asmbinary` on a different drive/volume: `AsmGen.kt:2093`
  `outputDir.sanitize().relativize(includedPath.sanitize())` throws `IllegalArgumentException` when
  the two paths have different roots, which escapes to `Compiler.kt:372` as
  `"\ninternal error"` + a rethrow. Pre-existing, but `-o D:\build\game.pgz` makes it reachable.
  Add an explicit guard with a clear diagnostic.
- A program output name containing dots (`my.game`) yields aux stem `my`. Cosmetic, matches shell
  conventions. Only applies to `-o`; the legacy path keeps its verbatim stem.
- `outputDir` and `outputFile` inside the `.p8ir` are absolute, so a `.p8ir` regenerated on
  machine B sends `prog8-newgen` back to machine A's build directory. Pre-existing for
  `outputDir`; `-o` doubles it. Consider falling back to the `.p8ir`'s own directory when the
  recorded paths do not exist.
- Directory creation failure today produces a raw `IOException` stack trace, not a clean error
  (`outputPath.createDirectories()` at `CompilerMain.kt:152` is unguarded). Worth a try/catch
  while touching this code.
- `outputDir` used to be described as "only used for artifact paths"; that is not accurate
  (it is also a `.binary` relativize base and is serialised into the `.p8ir`). The plan's design
  survives that, but the invariant should be asserted by tests, not assumed.
- `AMIGAHUNK` has no extension, so the artifact name and the aux stem coincide; under `-o` that
  means the artifact is `game` and the aux stem is `game`.
- `ConfigFileTarget.launchEmulator` throws - unaffected by the refactor.
- `assembler_options` in a target config can contain `--output` (`examples/customtarget/targetconfigs/f256.properties:63-65`);
  today it is neutralised by ordering (`AssemblyProgram6502.kt:35-38` puts
  `additionalAssemblerOptions` before `--output`). Keep that ordering and note it in `OutputFiles`.

## Docs

`docs/source/compiling.rst`:
- new `-o` / `--output` entry between `-noopt` (`:250`) and `-out` (`:254`)
- `:254-255` `-out`: note the mutual exclusion with `-o`
- fix the pre-existing bug at `:153` ("written to ``sourcefile.asm``" - it actually goes to the
  output directory, which defaults to the CWD per `CompilerMain.kt:91`, and no `.asm` is produced
  at all for `-target virtual`)
- `:163-164` "single dash or double dash" - satisfied by the `-o` / `--output` pair
- `:174` `-asmlist` -> `<program>.list` naming
- `:436-448` VICE/box16 symbol-file sections: the real name is `<stem>.vice-mon-list`, not
  `programname.vice-mon-list`
- note that `-o` is verbatim, that `%output` / `OutputType` still decides the content format,
  and that `-o` is ignored with `-noasm` / `-check` / `-dumpsymbols` / `-gendoc`

Also: `docs/source/technical.rst:111` and `programming.rst:1667` (`.bankedcalls`),
`binlibrary.rst:73` (`%output library` creates `<stem>.bin`), `profiling.rst:21,30`
(`-asmlist` listing naming), `docs/CODEBASE-KNOWLEDGE-GRAPH.md:116` (pipeline diagram),
a `docs/source/history.rst` changelog entry, and removing `docs/source/todo.rst:16`.

## Verification

```bash
gradle :compiler:compileKotlin --console=plain          # quick
nice gradle build --console=plain                        # full, per AGENTS.md
prog8c -target cx16 -asmlist -o /tmp/p8out/game.pgz examples/cx16/hello_cx16.p8 && ls /tmp/p8out
prog8c -target cx16 -o /tmp/p8out/game examples/cx16/hello_cx16.p8   # extension-less -o
prog8c -target virtual -o /tmp/p8out/vm.p8ir -emu examples/virtual/hello.p8
prog8c -target cx16 -o /tmp/x.prg -out /tmp/y examples/cx16/hello_cx16.p8   # must error
prog8c -target cx16 -o /tmp/p8out/game.pgz -emu examples/cx16/hello_cx16.p8
diff <(prog8c -target cx16 -printast2 examples/cx16/hello_cx16.p8 >/dev/null; cat hello_cx16.prg | wc -c) /dev/null  # default path unchanged
```