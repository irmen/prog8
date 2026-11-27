# Amiga shared libraries from Prog8

Status: design exploration. Nothing in this document is implemented yet.

## Goal

Prog8 should be able to build a real Amiga shared `.library` for `amiga500` and `amiga1200`, without hand-written boilerplate for the Exec ABI. The library must be loadable with `OpenLibrary("name.library", version)` and callable from C, assembly, or another Prog8 program through stable LVO offsets.

This is different from the existing Prog8 "binary loadable library" feature in `docs/source/binlibrary.rst`. That feature is a fixed-address blob mechanism for 6502 targets only. It explicitly rejects `%output library` on m68k targets.

## Amiga library model

An Amiga library is not an ELF shared object or a relocatable object loaded by a host loader. It is an AmigaDOS hunk executable with a ROMTag in the first code hunk. When `OpenLibrary()` finds a matching library on disk, Exec/ramlib loads it, finds the ROMTag, and initializes the resident module.

The essential runtime layout is:

```text
negative offsets from A6:
    -30   first user-defined LVO
    -24   reserved / future expansion
    -18   expunge
    -12   close
     -6   open
      0   struct Library                 <- library base, passed in A6
 positive:
     +... library data area, owned by this library instance
```

Each LVO slot is six bytes, because the classic 68000 `jmp absolute` instruction is six bytes. Callers use `jsr -N(a6)`, with `A6` set to the library base by `OpenLibrary()`. Read-only library globals belong in the positive data area if they must be addressed via the base. Mutable globals need explicit protection or a different ownership model.

The standard `struct Library` fields are defined in `exec/libraries.h`:

- `lib_Node`, including the library name and node type
- `lib_Flags`, including `LIBF_SUMMING`, `LIBF_CHANGED`, `LIBF_SUMUSED` and `LIBF_DELEXP`
- `lib_NegSize`, the number of bytes before the base
- `lib_PosSize`, the number of bytes after the base
- `lib_Version` and `lib_Revision`
- `lib_IdString`
- `lib_Sum`
- `lib_OpenCnt`

Relevant constants:

```text
LIB_VECTSIZE = 6
LIB_RESERVED = 4
LIB_BASE     = -6
LIB_USERDEF  = -30

LIB_OPEN     = -6
LIB_CLOSE    = -12
LIB_EXPUNGE  = -18
LIB_EXTFUNC  = -24
```

## Disk library boot chain

A disk-based library normally follows this chain:

```text
OpenLibrary("mylib.library", version)
  -> ramlib loads LIBS:mylib.library hunk file
  -> Exec finds the ROMTag in the first code hunk
  -> InitResident(romtag, segList)
  -> RTF_AUTOINIT table is interpreted as a MakeLibrary() call
  -> library-specific init routine runs
  -> Exec links the returned base into the library list
  -> caller receives the library base
```

The first code hunk starts with code equivalent to:

```asm
        moveq   #-1,d0
        rts
```

After that comes a `struct Resident` ROMTag with:

- `rt_MatchWord = RTC_MATCHWORD = $4AFC`
- `rt_MatchTag` pointing back to itself
- `rt_EndSkip` pointing just past the ROMTag
- `rt_Flags` containing `RTF_AUTOINIT`
- `rt_Type = NT_LIBRARY`
- `rt_Version`, `rt_Name` and `rt_IdString`
- `rt_Init` pointing to the four-longword autoinit table

With `RTF_AUTOINIT`, `rt_Init` does not point to code. It points to four longwords:

1. Size of the library base data area, including `struct Library`.
2. Pointer to the function table.
3. Pointer to an `InitStruct` data table, or `NULL`.
4. Pointer to the library init routine, or `NULL`.

The function table is an array of function pointers, or word displacements, terminated by `-1`. It must contain entries for the standard vectors first, then user vectors. `MakeLibrary()` expands this array into the six-byte jump table at negative offsets.

The init routine receives:

```text
D0 = library base
A0 = segList
A6 = ExecBase
```

and returns the library base in `D0`, or `NULL` on failure. The `segList` must be stored if expunge support needs to unload the code later.

## What MakeLibrary() is

`MakeLibrary()` is an Exec routine that constructs the in-memory library object. It allocates one memory block spanning the negative vector area, `struct Library`, and the positive data area, builds the jump vectors, initializes the base, and returns the address that callers will use as `A6`.

Signature:

```c
struct Library *MakeLibrary(APTR vectors,
                            struct InitStruct *structure,
                            APTR init,
                            ULONG dSize,
                            BPTR segList);
```

Register call:

```asm
    ; A0 = vectors
    ; A1 = structure, or NULL
    ; A2 = init routine, or NULL
    ; D0 = dSize
    ; D1 = segList
    jsr  _LVOMakeLibrary(a6)
    ; D0 = library base, or NULL
```

Important distinctions:

- `MakeLibrary()` does not create a disk file.
- It does not load code from disk.
- It does not by itself install a manually built library into Exec's library list.
- It is normally invoked by `InitResident()` via the `RTF_AUTOINIT` table.
- Manually created runtime libraries can use `MakeLibrary()` followed by `AddLibrary()`.
- `MakeFunctions()` is the lower-level Exec routine used by `MakeLibrary()` to build jump tables.

## Open, close and expunge

A library needs at least the standard vectors:

- `Open`: called for every `OpenLibrary()`. Increment `lib_OpenCnt` and return the library base in `D0`, or `NULL` to fail the open.
- `Close`: called for every `CloseLibrary()`. Decrement `lib_OpenCnt`.
- `Expunge`: called when Exec wants to remove the library. If `lib_OpenCnt != 0`, the library should defer removal, commonly by setting `LIBF_DELEXP`. If no users remain, release resources and unload the stored `segList`.

`AddLibrary()` and `RemLibrary()` are used when a library is constructed manually rather than loaded through the resident/disk path.

## Reentrancy and variable storage

Amiga library code is shared by all callers and can be entered recursively or concurrently from different tasks. Exec does not serialize calls. Therefore libraries must be re-entrant, or must protect mutable shared state explicitly.

Storage choices:

1. **Registers and caller stack**
   - Preferred for all per-call values.
   - Prog8 m68k ordinary locals and parameters use an activation record with `link a5,#-N`, so they are re-entrant.

2. **Library base positive data area**
   - Allocated once by `MakeLibrary()`.
   - Shared by all callers.
   - Safe for immutable values.
   - Mutable values need a `SignalSemaphore`, `ObtainSemaphore()`/`ReleaseSemaphore()`, `Forbid()`/`Permit()`, or a very short `Disable()`/`Enable()` section.
   - Do not hold locks across blocking OS calls.

3. **Per-task or per-open context**
   - Exec does not provide this automatically.
   - A library can allocate a context in `Open()`, return it as the base, or keep a protected list keyed by `FindTask(NULL)`.
   - Callers can also pass an explicit context pointer to every routine.

4. **Compiler-managed static storage in Prog8**
   - Globals, block variables, mutable arrays and strings, `@shared` variables, `memory()` slabs and virtual registers are static/shared on m68k targets.
   - They are not safe as scratch storage in a library unless protected or deliberately single-owner.
   - Constant, read-only data is safe to share.

An m68k library entry point receives `A6` as the library base. `A6` is not a scratch register for the library function. If the function needs Exec calls, it must store `SysBase` in the library data during init and load it when needed. The usual Amiga ABI preserves `D2`-`D7` and `A2`-`A6`; `D0`-`D1` and `A0`-`A1` are scratch.

## Current Prog8 state

The compiler currently has no m68k library output mode:

- `docs/source/binlibrary.rst` documents `%output library` as 6502-only and states that `%jmptable` is unavailable on m68k targets.
- `AstChecker.kt` rejects `%jmptable` with the message that library loading is 6502-only.
- `CallGraph.kt` treats `%jmptable` entries as references, which keeps exported subroutines alive.
- `SimplifiedAstMaker.kt` converts the directive into a `PtJmpTable` node.
- `PtJmpTable` contains ordered `PtIdentifier` children.
- The 6502 backend emits `jmp symbol` entries for each child.
- The IR code generator lowers `PtJmpTable` to `IRInstructions.jump(...)`.
- The m68k backend would emit a `bra` for that IR jump, which is neither six bytes nor a data/vector table.

The 6502 mechanism cannot be reused directly. Its jump table is executable code at a forward address, while the Amiga LVO table lives at negative offsets from a runtime library base and is built from a function-pointer array by `MakeLibrary()`.

## Can `%jmptable` be reused?

Yes as the front-end export list, but not as-is.

Useful existing behavior:

- Entries are validated as subroutine names.
- Entries are marked as references, so the optimizer keeps the routines.
- Ordering is preserved into a simple AST node.
- It already expresses "these routines have a stable external calling interface".

Required changes:

- Allow `%jmptable` on m68k only under library output.
- Add explicit LVO numbers, otherwise inserting a routine silently shifts later offsets.
- Emit a raw data/code chunk that bypasses IR control-flow optimization.
- Emit either a six-byte `jmp` table or a `dc.l` function-pointer table terminated by `-1`.
- Add the standard vectors before user entries.
- Keep a stable mapping from LVO number to routine for generated client bindings.

The current IR representation is unsuitable for the vector table because `IRInstructions.jump()` is control flow. The optimizer and register allocator can legally transform or remove it. A new raw chunk or a dedicated IR instruction is needed for ABI tables.

## Proposed syntax

Sketch, not final:

```prog8
%output library
%libname "mylib.library"
%libversion 1
%librevision 0
%libid "mylib 1.0 (27.11.2026)"
%option reentrant

main {
    %jmptable (
        1=func1,
        2=func2,
        5=func3,
    )

    sub start() {
        ; one-time library initialization
    }
}
```

Interpretation:

- LVO numbers are user-visible API numbers.
- Actual A6 offsets are `-30 - 6*(n-1)` for user vector `n`.
- Standard vectors are generated automatically unless the user supplies `open`, `close` or `expunge` hooks.
- `%option reentrant` rejects mutable static allocation in library code.
- Explicit library data may be introduced with a syntax that states its protection model, for example a locked shared object or a per-task context.

Possible client-side usage:

```prog8
%import mylib

main {
    sub start() {
        mylib.func1()
    }
}
```

The import would generate a call through `A6` using the known LVO and manage `OpenLibrary()`/`CloseLibrary()` in a way similar to existing AmigaOS library modules.

## Compiler work needed

### 1. Library output mode

Add an m68k `%output library` mode that:

- suppresses normal Prog8 program startup
- emits a hunk executable suitable for `LIBS:`
- emits the `moveq #-1,d0; rts` prefix
- emits the ROMTag and autoinit table
- emits name, version, revision and id string
- stores `segList`
- generates default `Open`, `Close` and `Expunge` routines
- generates or calls `InitResident()`/`MakeLibrary()` through the autoinit table

### 2. Export table lowering

Replace or augment `PtJmpTable` lowering for library mode:

- parse explicit LVO numbers
- validate duplicate and missing LVO numbers
- reserve the standard vectors
- emit a raw function-pointer array for autoinit
- optionally emit a physical six-byte `jmp` table for a non-autoinit variant
- preserve relocations for all function pointers

### 3. Reentrant checking

Add a library-safe mode that rejects:

- mutable globals and block variables
- mutable arrays and strings
- mutable `@shared` variables
- mutable `memory()` slabs used as scratch
- virtual registers mapped to static storage
- mutable static state in imported modules

Allow read-only constants and ABI tables. Provide an explicit escape hatch for protected shared state.

### 4. A6-safe entry points

Exported routines must:

- treat `A6` as the library base
- avoid clobbering `A6`
- preserve registers required by the Amiga ABI
- return results in the expected registers, usually `D0`

### 5. Segment and lifecycle support

Generate correct handling for:

- `OpenCnt`
- delayed expunge
- `LIBF_DELEXP`
- resource cleanup
- `UnLoadSeg()` only when no users remain

### 6. Client bindings

Generate enough information for callers:

- LVO constants for C and assembly users
- Prog8 import stubs that call through the library base
- version checking at `OpenLibrary()` time
- automatic close at program exit, matching existing AmigaOS library modules

## Implementation sequence

1. **Assembly shim prototype**
   - Hand-write the ROMTag, autoinit table, standard vectors and `MakeLibrary()` call.
   - Put the real routine bodies in Prog8-generated assembly.
   - Verify with `vamos --cpu 68000` and `copperline --model A500`.
   - Confirm two tasks can call the same routine concurrently.

2. **Raw table support**
   - Let Prog8 emit explicit LVO tables as data, bypassing IR control flow.
   - Add tests that inspect generated `.asm` text.

3. **Full library output**
   - Add `%output library` and library metadata directives.
   - Generate the whole resident/init wrapper.
   - Add default lifecycle vectors.

4. **Reentrant mode and bindings**
   - Add static-state validation.
   - Generate client LVO constants and Prog8 import stubs.

## Testing notes

- Unit tests can use `writeAssembly = true, assemble = false` and inspect `.asm` lines for vector layout and emitted directives.
- CI agents do not have `vasm`, so tests that require final hunk assembly cannot depend on it there.
- Functional tests should run on `amiga500` with `vamos` or `copperline`.
- `qemu68k` uses a different OS environment, so native Amiga library loading is mainly an `amiga500`/`amiga1200` concern.
- Test cases should include:
  - version match and mismatch
  - multiple tasks calling the same routine
  - open and close counting
  - expunge with and without open users
  - explicit LVO additions not shifting existing numbers
  - no mutable static state in reentrant mode
  - protected shared state with an explicit annotation

## Open questions

- Should `%jmptable` gain explicit LVO syntax, or should that be a separate `%lvo` directive?
- How should user-supplied `Open`, `Close` and `Expunge` hooks be declared?
- What is the minimal safe shared-state annotation: locked global, per-task context, or both?
- Should `OpenLibrary()` return a per-open context base, or always the single shared base?
- How should client LVO constants be exported for C and assembly users?
- How much of the AmigaOS startup initialization can a library assume, given that `OpenLibrary()` may be called from tasks that are not DOS processes?
- How should libraries avoid clashing with Prog8's use of `A5` as the m68k frame pointer and `A6` as the Amiga library base?

## References

- `docs/source/binlibrary.rst`: existing 6502-only binary loadable libraries.
- `docs/source/amigalibraries.rst`: existing AmigaOS library bindings.
- `home-irmen/Documents/prog8-dev-resources/Amiga-NDK/Include_H/exec/libraries.h`: `struct Library` and vector constants.
- `home-irmen/Documents/prog8-dev-resources/Amiga-NDK/Include_H/exec/resident.h`: `struct Resident`, `RTC_MATCHWORD`, `RTF_AUTOINIT`.
- `home-irmen/Documents/prog8-dev-resources/Amiga-NDK/Autodocs/exec.doc`: `MakeLibrary()`, `MakeFunctions()`, `InitResident()`, `AddLibrary()`, `OpenLibrary()`.
- `home-irmen/Documents/prog8-dev-resources/Amiga-NDK/DAControl+trackfile/trackfile/trackfile_device.c`: real RTF_AUTOINIT device skeleton.
