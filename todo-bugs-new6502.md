# Known bugs in the experimental new 6502 codegen (`-newcodegen`)

Pre-existing issues in the IR-based 6502 backend (`codeGenNew6502`), unrelated to
the union-types feature. All of them only affect programs compiled with
`-newcodegen`; the legacy backend (`codeGenCpu6502`) handles all these cases
correctly. The CLI itself labels the new backend as experimental/incomplete
("Not for production code").

## 1. Compiler crash on float `ADDIM`/`SUBIM` opcodes

**Severity: compiler crash (loud, no bad output produced)**

The IR codegen lowers `floatvar += constant` into a dedicated "add immediate to
memory" opcode:

```
addim.f #3.141592653589793.f,[floats.atan2.atn]
```

(emitted by `codeGenIntermediate/.../AssignmentGen.kt`, `memoryOpImmediateFloat(Opcode.ADDIM, ...)`).

`translateFloatArithmetic` in `codeGenNew6502/.../InstrArithmetic.kt` implements
the register (`ADDR`), immediate-register (`ADD`) and memory (`ADDM`) variants
for float arithmetic, but not the combined memory+immediate opcodes `ADDIM` /
`SUBIM`. These fall through to:

```kotlin
else -> TODO("Unsupported float arithmetic opcode: ${insn.opcode}")   // InstrArithmetic.kt:1660
```

which crashes the compiler with `kotlin.NotImplementedError`
("internal error: missing feature/code").

**Trigger:** any program containing `floatvar += const` (or `-=`), compiled
with `-newcodegen`.

It surfaces easily via library code: with `-noopt` (or `optimize=false` in
tests), unused-code removal is disabled, so merely `%import floats` compiles
the entire float library - and its `atan2` sub contains `atn += π`
(`compiler/res/prog8lib/virtual/floats.p8:110`, similar for other targets).
With optimizations enabled (default), unused library subs are removed and
simple float programs compile fine, which hides the bug.

**Fix direction:** implement `ADDIM`/`SUBIM` (and float `MULIM`/`DIVIM` if the
IR generator can produce them) in `translateFloatArithmetic`, or fold them
into the existing memory variants.

## 2. BSS struct instances fail to assemble ("not defined symbol")

**Severity: compilation fails at assembly stage (loud)**

For every struct allocation, the IR generator bakes the address label into the
IR as `prog8_struct_instances.<name>`, unconditionally - even for
*uninitialized* instances (`codeGenIntermediate/.../BuiltinFuncGen.kt:700`).

The legacy backend has no problem because it emits these references itself and
selects the correct block prefix per instance: empty initializer ->
`prog8_struct_instances_bss`, otherwise `prog8_struct_instances`
(`codeGenCpu6502/.../BuiltinFunctionsAsmGen.kt:725`).

The new backend however:

- emits uninitialized instances into a *different* 64tass block
  (`prog8_struct_instances_bss`, `codeGenNew6502/.../AsmGen.kt`, data section
  emission), with the block prefix stripped from the label
  (`label.substringAfter('.')`),
- while generated code still references `prog8_struct_instances.main_S_...`.

64tass then reports `not defined symbol 'prog8_struct_instances...'` and
compilation fails. Initialized instances work because their block happens to be
named `prog8_struct_instances`, matching the reference prefix.

**Trigger:** any `^^S s = ^^S : []` (empty initializer) compiled with
`-newcodegen`. This predates the union feature; union instances merely join
the same broken path because all unions are routed into the no-init partition
(`|| def.isUnion`).

Note: the m68k backend escapes this by emitting the full dotted name as a flat
label (vasm allows dots in labels, no block scoping).

**Fix direction:** either make the IR generator emit the `_bss` prefix for
uninitialized instances (mirroring the legacy backend's logic), or emit the
BSS instances in a way that matches the baked-in reference name.

## 3. Initialized struct instances corrupt float and long fields

**Severity: silent data corruption (worst of the three)**

The struct-instance data emission in `codeGenNew6502/.../AsmGen.kt`
(`IRStSymbolicReference.Numeric` case) maps field types to assembly
directives with an `else -> ".word"` catch-all. `FLOAT` and `LONG` both fall
into it:

```kotlin
val size = when (fieldValue.dt) {
    BaseDataType.UBYTE, BaseDataType.BYTE, BaseDataType.BOOL -> ".byte"
    BaseDataType.UWORD, BaseDataType.WORD -> ".word"
    else -> ".word"      // FLOAT and LONG end up here
}
emitLine("  $size $v")   // v = fv.value.toInt()
```

Consequences for an initialized instance:

- a `float` field (5-byte MFLPT on 6502) is emitted as a 2-byte `.word` holding
  the *truncated integer* value. Example: `^^P p = ^^P : [1.5, 42]` produces

  ```
  .word 1
  .byte 42
  ```

  so `p.x` reads a garbage float;
- a `long` field (4 bytes) likewise gets only 2 bytes;
- because the emitted data is 3 (resp. 2) bytes short, every field after the
  float/long is shifted, corrupting the whole instance.

64tass accepts the output, so nothing fails: the program just misbehaves at
runtime.

**Trigger:** any initialized struct instance (`^^S : [...]`) with a `float`
or `long` field, compiled with `-newcodegen`.

**Fix direction:** emit float fields as the 5 MFLPT bytes
(`target.getFloatAsmBytes(value)`), and long fields as `.dint`/4 bytes.
(The legacy backend already does the float case correctly via a bracketed
`.byte` byte list.)
