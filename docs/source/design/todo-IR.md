# Language Features and Gaps in the IR / .p8ir File Format

## Background and Scope

This document tracks what is and isn't represented in the IR that the compiler
emits to `.p8ir` files. The goal is to determine whether a new code generator
backend (e.g. a new 6502 backend, or a 68000 backend) built on top of the IR
could reproduce every language feature, simply by reading the `.p8ir` file.

**Important: this document is about the IR representation, not the Virtual
Machine.** The question is whether the information needed to reproduce a
feature is present in the IR data structures and survives a write/read
round-trip through the `.p8ir` file. It does NOT matter whether the built-in
Virtual Machine can actually execute the feature.

Many features that the Virtual Machine refuses to run (because, for instance,
the VM doesn't store code in actual memory and so cannot honour memory
alignments, or because the VM doesn't model bank switching) are still very
much needed in the IR. A 6502 backend has real memory layout, real code
addresses, and real bank-switching semantics, and it must be able to read all
of that information out of the `.p8ir` file.

**Example:** block-level `%align 256` makes no sense to implement in the
Virtual Machine because the VM doesn't put program code in actual memory at
specific addresses. But the directive IS meaningful for a 6502 backend that
emits 64tass code and must produce a `.align 256` directive in the output.
The information must be present in the IR even though the VM ignores it.

A feature is "preserved" if the IR data structures hold it and
`IRFileWriter` / `IRFileReader` round-trip it through the XML `.p8ir` format.
A feature is "broken" if `IRCodeGen` throws or crashes when encountering it,
which means no `.p8ir` file is produced at all - in which case a new backend
that only reads `.p8ir` will never even see the program.

The Virtual Machine's own implementation status of a feature is a separate
question, covered briefly in `docs/source/todo.rst` and not duplicated here.

All file:line references are in the current source tree
(`codeGenIntermediate/src/prog8/codegen/intermediate/` unless noted otherwise).

## Summary

Roughly 95% of the language is fully preserved in the IR. The remaining gaps
are concentrated in three areas:

1. A handful of expression-translation edge cases that throw `TODO(...)` or
   `AssemblyError` during IR generation. None of these need VM support; they
   are simply missing translations from the Simple AST to the IR.
2. Two block-level constructs that fail outright at IR generation: `%align`
   inside a block and `&blockname` (address-of of a whole block). The
   first is a real 6502 / native-backend concern (alignment of code in
   memory) that the VM cannot use but a real backend must.
3. Architectural concerns that a new backend must solve itself: zero-page
   allocation is not encoded in the IR, and cross-module library linking is
   not represented in the `.p8ir` file format.

## Features Preserved in `.p8ir`

The following language features are fully encoded in the IR and round-trip
through the file format. A new backend can read them out of the `.p8ir` file
and emit equivalent code for the target CPU. Whether the Virtual Machine can
execute the feature is irrelevant to this list.

| Feature | IR / .p8ir representation | Notes |
|---|---|---|
| `-D SYMBOL=VALUE` command-line flag | `<ASMSYMBOLS>` element with `NAME=VALUE` lines | Round-trips; a 6502 backend can emit these as `NAME = VALUE` directives in the .asm file header. The VM ignores them, but they are useful for passing build-time values to the generated assembly. |
| `%asmbinary` | `<BYTES>` element with raw hex bytes | Round-trips cleanly; a 6502 backend can emit `.binary` for 64tass. The VM cannot load these chunks - that limitation is in the VM, not the IR. |
| `%asm` (inline assembly) | `<ASM>` element with `IR="true"` or `IR="false"` | Assembly text is preserved verbatim. The VM refuses to execute these chunks; a 6502 backend emits the assembly as-is. |
| `inline asmsub` | `<ASMSUB>` with name, clobbers, params, returns, asm | Including statusflag returns/params (`@Pz`, `@Pc`, etc.) |
| `extsub` (with fixed address) | `<ASMSUB>` with non-empty `ADDRESS` attribute | Calls become direct address references |
| `%align` (statement form) | `ALIGN <n>` opcode | The block-level form does NOT work (see below) |
| Variable alignment | `align=N` attribute in `<VARS>` entries | For both `<NOINITCLEAN>`, `<NOINITDIRTY>`, and `<INIT>` |
| Memory slab alignment | Third field in `<MEMORYSLABS>` lines | e.g. `prog8_slabs.memory_arena 4000 0` |
| `&label`, `&subroutine`, `&variable` (address-of) | `LOAD <dt> <reg>,<symbol>` | Verified; `&main.start` produces `load.w r1,main.start` |
| Computed address (`&arr + offset`, struct field address) | `ADDR` and `LOAD` combinations | Works through existing IR opcodes |
| All binary operators (`+ - * / % << >> & \| ^`) | Dedicated `Opcode` enum values | `Opcode.SUB`, `Opcode.MUL`, etc. |
| All comparison operators | `Opcode.BLT`, `BGT`, `BLES`, etc. | Includes signed and unsigned variants |
| Short-circuit `and` / `or` | Branch chains | No special opcode; uses existing branch instructions |
| `defer` (compiled to trampolines) | Normal `CALL` to defer subroutines | Already in IR; no special handling needed |
| `for`, `repeat`, `while` loops | Branch + label chunks | Standard structured translation |
| `goto` / labels | `JUMP` with `labelSymbol` | Both forward and backward |
| `switch` / jumptable | Sequence of `JUMP labelSymbol` instructions | |
| `breakpoint` | `BREAKPOINT` opcode | |
| Struct definitions, struct field access, `NEW`, `^^type` | Existing IR instructions for pointer access and ADDR | Verified with `examples/pointers/fountain-virtual.p8` |
| Pointer arithmetic, typed pointers, `&struct.field` | `LOAD` + `ADDR` / `ADD` | |
| `callfar` / `callfar2` | `SYSCALL <num>(...)` with `CALLFAR` / `CALLFARVB` syscall numbers | Encoded; the VM refuses to execute them, but they ARE in the .p8ir |
| `CALLI` (indirect call) | `CALLI` opcode with register operand | Encoded |
| Inline IR code (`%ir {{ ... }}`) | `<ASM>` element with `IR="true"` | |
| Multiple return values from a sub | `RETURNS=` attribute on `<SUB>` and `fcallArgs` | |
| String variables | `ubyte[N]` byte arrays in `<INIT>` with `,0` terminator | |
| Constants, memory-mapped vars, memory slabs | `<CONSTANTS>`, `<MEMORYMAPPED>`, `<MEMORYSLABS>` sections | |
| ZP string/array initialization | `SYSCALL` (MEMCOPY) in `<INITGLOBALS>` | Recently fixed; see `initglobals-ir-issue.md` |
| Romable mode flags | `romable=true/false` in `<OPTIONS>` | |
| Target CPU / ZP / output dir | `<OPTIONS>` block | |
| Library code | `<BLOCK LIBRARY="true">` with full sub bodies | All inlined into the single .p8ir file |
| `DEFFLAG` / `DEFBYTE` initialization of globals | Translated into IR `LOAD`+`STOREM` sequences | |

## Features Broken at IR Generation

These features cause `IRCodeGen` to throw, so no `.p8ir` is produced. A new
backend that only consumes `.p8ir` files cannot handle programs that use
them - not because the VM cannot run them, but because the compiler fails
before any `.p8ir` file is written.

None of these gaps are due to VM limitations; they are gaps in the
AST-to-IR translation. Note that language features blocked at the AST level
(struct parameters, struct values in arrays, non-`@Pc` status flag
parameters, etc.) are documented in `docs/source/todo.rst` and not repeated
here, because they never reach the IR generator.

Item 2 below is a borderline case: the same pattern also crashes the
existing 6502 codegen, and the 6502 codegen actually crashes on *more*
patterns than the IR codegen (no fallthrough to the runtime path for
non-const indexes). It is listed here because the IR codegen is part
of the bug and because a new 6502-from-IR backend would inherit the
same problem.

### 1. Various `TODO(...)` in `ExpressionGen.kt`

These show up when a particular code path is taken during expression
translation. The comment for each explains the gap:

- `ExpressionGen.kt:111` - `didn't expect PtConstant to appear here`
- `ExpressionGen.kt:119` - same, in another context
- `ExpressionGen.kt:150` - `translate structinstance deref???`
- `ExpressionGen.kt:392` - `LOAD memory() address?` (asking how to get the
  address of a `memory()` slab)
- `ExpressionGen.kt:887` - `typecast to array of pointers`
- `ExpressionGen.kt:1738` - `get pointer from deref` (asking how to take the
  address of the value a pointer dereference yields)

None of these are VM concerns; the VM doesn't even get to see the program
because the IR translator throws first.

### 2. Pointer-deref array indexing in built-in functions (both backends)

Calling `rol`, `ror`, `rol2`, `ror2`, `setlsb`, or `setmsb` on an array
field reached through a typed pointer (e.g. `rol(ptr.arr[2])` or
`setlsb(ptr.arr[i], v)` where `ptr` is `^^Data.Node` and `Data.Node`
has a `uword[5] arr` field) crashes both backends. This is a
compiler-wide limitation, not an IR-specific gap — but the two
backends differ in *which* patterns they crash on.

| Pattern | IR codegen | 6502 codegen |
|---|---|---|
| `rol(plain[const])` | works | works |
| `rol(ptr.arr[const])` | crash (line 767) | crash (line 1024) |
| `rol(ptr.arr[idx])` | **works** (falls through to line 794) | crash (line 1024) |
| `setlsb(ptr.arr[i], v)` | crash (line 835, any index) | crash (line 1139, any index) |

**IR codegen** (`codeGenIntermediate/.../BuiltinFuncGen.kt`):

- Line 767 — `funcRolRor` TODO; fires for `rol`, `ror`, `rol2`, `ror2`
  with a *const* index. Non-const index falls through to the general
  code path at line 794.
- Line 835 — `funcSetLsbMsb` TODO; fires for `setlsb` / `setmsb` with
  *any* index (const or not). The non-const path at line 861 is
  unreachable because the `target.variable==null` guard is checked
  first.
- Line 870 — `funcSetLsbMsb` TODO; unreachable. The non-split-words
  branch is only reachable via a `PtIdentifier` base, in which case
  `target.variable` is non-null. The AST check at
  `AstChecker.kt:1945-1946` rejects `setlsb(ptr.val, v)`-style calls
  first.

**6502 codegen** (`codeGenCpu6502/.../BuiltinFunctionsAsmGen.kt`): no
fallthrough for any of the affected patterns. TODOs at `funcRor2:763`,
`funcRor:841`, `funcRol2:950`, `funcRol:1024` (all fire for any
pointer-deref-base indexer, const or not). TODO at `funcSetLsbMsb:1139`
and `funcMsb:2398` for the same reason.

The fix in both backends is to fall through to the existing non-const
code path (using `translateExpression(arg)` in the IR codegen, the
`asmgen.loadScaledArrayIndexIntoRegister` helper in the 6502 codegen)
instead of throwing. The cost is a small loss of optimization for the
affected patterns. See `examples/test.p8` for the working and crashing
patterns on each target.

### 3. Generic fallback `TODO("missing codegen for $node")`

`IRCodeGen.kt:443` has a catch-all `else -> TODO("missing codegen for $node
${node.position}")` in the `translateNode` function. If a future Prog8 feature
introduces a new Pt node type that isn't handled, this is where it will
land. Currently no known production code path hits this, but it is a
fragile spot: a new IR backend cannot assume every program compiles to
the IR cleanly.

## Features with Suboptimal / Degraded Translation

These are not broken, but the IR codegen produces working but suboptimal
output. A new backend will produce correct code, but it may be larger or
slower than the 6502-codegen path. None of these involve the VM.

### In-place arithmetic on arrays

`AssignmentGen.kt` has several `TODO` comments marking optimized in-place
array operations that fall through to `return null` and trigger a
load-modify-store sequence:

- `AssignmentGen.kt:1313` - `/ in array`
- `AssignmentGen.kt:1316` - "optimized memory in-place /"
- `AssignmentGen.kt:1430` - "optimized memory in-place *"
- `AssignmentGen.kt:2060` - "optimized >> in array"
- `AssignmentGen.kt:2063` - "optimized memory in-place >>"
- `AssignmentGen.kt:2119` - "optimized << in array"
- `AssignmentGen.kt:2122` - "optimized memory in-place <<"
- `AssignmentGen.kt:2286` - `% in array`
- `AssignmentGen.kt:2289` - "optimized memory in-place %"

These work correctly, just less efficiently than the 6502-codegen path can
do.

### Split-word array logical OR (non-const)

`AssignmentGen.kt:1213` has an explicit comment:

```
// Non-const cases - split words not supported for logical or
```

For non-constant right-hand sides, the logical-or assignment on a split-word
array does not generate optimized code.

## What the VM Refuses is Not the Same as What the IR is Missing

It is important to keep two lists separate:

**The IR is missing these features** (no `.p8ir` is produced, or the
information is lost) - documented in the "Broken at IR Generation" and
"Suboptimal" sections above.

**The VM refuses to execute these features** (the `.p8ir` IS produced and
contains the information) - documented in `docs/source/todo.rst` under
"IR/VM" and "Missing VM Implementations". These include:

- `IRInlineAsmChunk` and `IRInlineBinaryChunk` cannot be loaded by the VM
  (because the VM doesn't load programs as memory-mapped data, only as
  instructions). The information is in the `.p8ir` regardless.
- VM cannot load a label address as a value (it tracks no code addresses).
  The information is in the `.p8ir` regardless.
- `CALLI`, `CALLFAR`, `CALLFARVB` opcodes are rejected at runtime (the
  VM has no way to jump to a code address). The opcodes are in the `.p8ir`.
- `CLI` / `SEI` opcodes are rejected (the VM doesn't model interrupt state).
  The opcodes are in the `.p8ir`.
- Unsigned long `MUL` / `DIV` / `MOD` / `DIVMOD` are rejected (the VM
  doesn't implement them). The opcodes are in the `.p8ir`.

All of the above are perfectly valid in the IR and a 6502 backend can
implement them; they are VM limitations only.

## Architectural Concerns (not bugs, but design gaps)

These are not "broken features" in the same sense as the above. They are
design choices in the IR that a new backend must deal with, and they are
already documented in `docs/source/design/`. They are listed here for
completeness.

### 1. Zero-page allocation is up to each backend

The IR's `IRStStaticVariable` carries the programmer's intent
(`zpwish: ZeropageWish`: REQUIRE, PREFER, DONTCARE, NOT_IN_ZEROPAGE)
but no allocation result. This is intentional: zero-page allocation
is target-specific (m68000 has no ZP, 6502 has 256 bytes of which
~100 are free, 65C02 may have a different layout), and targets with
a ZP need to know about scratch register conventions, free ranges,
and type-size scoring to do a good job.

**Design choice:** each IR-based backend that has a ZP (or equivalent
fast-access region) runs its own allocator after reading the .p8ir
file. The IR remains simple and target-agnostic. There is no
`zpAddress` field in `IRStStaticVariable`, and no `zpaddr=` attribute
in the variable line format.

The programmer's `zpwish` is the only signal a backend has to start
from: REQUIRE_ZEROPAGE vars must end up in ZP or the program fails
to compile; PREFER_ZEROPAGE vars should if there is room; DONTCARE
vars compete for remaining space; NOT_IN_ZEROPAGE vars are excluded.

The init-copy pattern (ZP string/array → ROM at startup) is also a
backend responsibility. A backend that allocates a ZP variable with
an init value must emit the appropriate startup copy code itself
(see `ProgramAndVarsGen.kt:617-682` for the existing 6502 implementation).

The current `new6502gen` module is not in the source tree; when it
is added, it will need to implement its own ZP allocator.

### 2. Library .p8ir files are not linked externally

A new backend that wants to consume a `.p8ir` file produced by the IR
codegen will get the full program in one file, including all imported
libraries. This is fine, but the libraries are emitted as ordinary blocks
in the same file (with `LIBRARY="true"`). A new backend will need to handle
this case (mark library code as not requiring separate compilation, dedupe
against the standard library it might be linking itself, etc.).

A cleaner design might be to have a multi-file `.p8ir` linker step that
resolves library references across files, but this does not currently exist.

### 4. 6502-specific optimizations in `codeOptimizers/`

Per `docs/source/todo.rst:82`:

```
various optimizers skip stuff if compTarget.name==VMTarget.NAME. Once
6502-codegen is done from IR code, those 6502 only optimizations should
probably be removed
```

A new 6502 backend built on the IR should drop the 6502-specific
optimizations in `codeOptimizers/` and instead rely on the IR's own
`IRPeepholeOptimizer`. These two optimization stages currently co-exist.

## Recommendations for a New Backend Author

1. The IR / .p8ir file format is rich enough that a new 6502 backend can
   reproduce nearly all language features by reading the .p8ir file.
   The VM's inability to run a feature (alignment, inline asm, inline
   binary, far calls, indirect calls, CLI/SEI, unsigned long math) is
   irrelevant - that information is in the IR and a 6502 backend can
   implement it.

2. Before claiming "full language support", test the new backend against:
   - Programs using `cast(array of pointers)` (broken in IR generation)
   - Programs using `cast(<typecast to pointer>)` followed by indexing
     (broken in IR generation)
   - Programs using `&memory()` to get the address of a memory slab
     (broken in IR generation)
   - Programs using `setlsb()` or `setmsb()` on a typed pointer's
     array field (`setlsb(ptr.arr[i], v)`) — crashes regardless of
     whether the index is const
   - Programs using `rol()` / `ror()` / `rol2()` / `ror2()` with a
     *const* index on a typed pointer's array field
     (`rol(ptr.arr[2])`) — non-const index works

3. If your backend has a ZP (or equivalent fast-access region), run
   your own ZP allocator over the IR symbol table, honouring the
   `zpwish` field. Emit the init-copy pattern (ZP string/array init)
   yourself; the IR does not carry that information.

4. Consider running both the IR's `IRPeepholeOptimizer` and your own
   backend-specific peephole optimizer, until the redundant 6502-only
   optimizations in `codeOptimizers/` are removed.

5. Watch for the catch-all `TODO("missing codegen for $node")` at
   `IRCodeGen.kt:443` - if a new Pt node type is added to the language
   and isn't handled in the IR, it will fail here and your backend will
   never see the program.

## Related Documentation

- `docs/source/design/initglobals-ir-issue.md` - the recent fix for
  ZP string/array initialization in the IR
- `docs/source/design/zp-ir-issue.md` - the open design issue for ZP
  allocation in the IR
- `docs/source/todo.rst` section "IR/VM" - VM-specific gaps (separate
  concern from this document, which is about the IR representation)
- `docs/source/technical.rst` - architectural overview of the IR pipeline
- `intermediate/src/prog8/intermediate/IRInstructions.kt` - the full
  opcode reference
- `intermediate/src/prog8/intermediate/IRFileWriter.kt` - the writer
  showing exactly what is serialized
