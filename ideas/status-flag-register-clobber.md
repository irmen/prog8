# Status flag extraction clobbers a register return value

**Status: deferred / design only, not implemented.** Listed in
`docs/source/todo.rst`.

Note: the *multiple* status flag returns case is already fixed (each flag
extraction is wrapped in `PUSHST`/`POPST` in `AssignmentGen`). This document
covers the separate case where a status flag return is combined with a regular
register return that lives in the extraction's scratch register.

## 1. Problem

In a multi-assignment where one call returns both a CPU status flag and a
regular register value, the flag extraction can destroy the register return
before it is read. Example:

```prog8
main {
    inline asmsub f(ubyte arg @A) -> bool @Pc, ubyte @A {
        %asm {{
            lda #42
            cmp #42
        }}
    }

    sub start() {
        bool c
        ubyte v
        c, v = f(42)      ; v is NOT 42, it is the clobbered scratch value
    }
}
```

This is reachable in practice, e.g. the f256 syslib `GETIN() -> bool @Pc, ubyte @A`
(`examples/customtarget/libraries/f256/syslib.p8:39`).

The bug is *separate* from the "multiple status flag returns clobber each other"
issue (which was fixed by wrapping each flag extraction in `PUSHST`/`POPST` in
`AssignmentGen`). That fix protects the status bits; it does not protect a
hardware register that holds a non-flag return value.

### When it triggers

The bug needs a **multi-assign** from an `asmsub`/`extsub` (only those can
declare status flag returns) whose returned **status flag target is not `void`**
and whose **register return target is not `void`**, where the register value
lives in the extraction's scratch register:

- **new6502 / 6502**: scratch is `A`.
  - `@A` (byte/bool) return: broken with *any* flag (`@Pc`/`@Pz`/`@Pn`/`@Pv`),
    because every flag extraction loads a `0`/`1` immediate through `A`.
  - `@AX`/`@AY` (word) return: broken too, since `A` is its low byte.
  - `@X`/`@Y`-only returns: safe.
- **m68k**: scratch is `D0`, but only the `@Pc` extraction uses it (the `ROXL`
    round-trip). `@Pz`/`@Pn`/`@Pv` lower to a branch plus a direct memory store.
  - `@D0` + `@Pc`: broken.
  - `@D0` + `@Pz`/`@Pn`/`@Pv`: safe.
  - `@D1` and higher: safe.
- **virtual (VM)**: never (extraction only uses IR virtual registers).

The return order does not matter (flags are always extracted before the register
returns), and it is irrelevant where the value is stored (variable, register, or
memory).

## 2. Root cause

The IR generator (`codeGenIntermediate/src/prog8/codegen/intermediate/AssignmentGen.kt`)
processes a multi-assign in this order:

1. **status flag returns first** (they must read the live CPU status right after
   the call), then
2. **non-flag register returns** via `LOADHR` (hardware register -> IR virtual
   register).

The status flag extraction sequence uses a **scratch hardware register**:

| target | extraction scratch | where |
| --- | --- | --- |
| new6502 | `A` | `lda #0` before `ROXL`, and the `lda`-based stores |
| m68k | `D0` | `ROXL` is lowered as `move.b regfile,d0; roxl.b #1,d0; move.b d0,regfile` (`codeGenM68k/.../InstrBitwise.kt:357`) |
| virtual (VM) | none (operates on IR virtual registers only) | unaffected |

If a non-flag return lives in that same scratch register, the extraction
destroys it before `LOADHR` reads it.

### Evidence (new6502, `-target c64 -newcodegen`)

```
; call p8b_main.p8s_f():r1.b@Pc,r2.b@s0
; inlined: p8b_main.p8s_f
lda #42
cmp #42
; end inlined
; load.b r1.b,#0.b
lda #0                  <-- clobbers A
sta p8_regfile+0
; roxl.b r1.b
rol p8_regfile+0
; storem.b r1.b,[c]
lda p8_regfile+0
sta c
; loadhr.b r2.b,s0.b    <-- reads the clobbered A, not 42
sta p8_regfile+1
```

### Evidence (m68k, `-target qemu68k`)

```
bsr p8b_main.p8s_f
; load.b r1.b,#0.b
clr.b p8_regfile+0
; roxl.b r1.b
move.b p8_regfile+0,d0   <-- clobbers d0 (which held the @D0 return)
roxl.b #1,d0
; storem.b r1.b,[c]
move.b d0,p8v_c
; loadhr.b r2.b,s10.b
; storem.b r2.b,[v]
move.b d0,p8v_v          <-- stores the clobbered d0
```

### Legacy 6502 reference

The old 6502 codegen handles the byte/bool-in-`A` case with `pha`/`pla`:

- `codeGenCpu6502/.../assignment/AssignmentAsmGen.kt:69-76` - if a byte/bool
  return is in `A` and there are status flags, emit `pha` before the flag
  extraction.
- `:103-106` - `pla` before storing the register results.

Note that `hasByteInA` (`:69`) only checks `ret.type.isByteOrBool &&
registerOrPair == A`, so legacy does **not** cover a word return in `@AX`/`@AY`
and shares that variant of the bug (the low byte in `A` is clobbered). The
proposed design below covers all of these cases.

## 3. Design

### 3.1 Key enabler: register-neutral PUSHST/POPST on m68k

The recommended fix relies on `PUSHST`/`POPST` not clobbering any data register.
On new6502 they are already neutral (`php`/`plp`). On m68k the current
implementation goes through `d0`:

```
move ccr,d0
move.b d0,-(sp)     ; PUSHST
move.b (sp)+,d0
move d0,ccr         ; POPST
```

The 68010 `MOVE CCR` instruction also supports a **memory** operand, so the
round-trip is unnecessary:

```
move ccr,-(sp)      ; PUSHST   (encoding 42E7)
move (sp)+,ccr      ; POPST    (encoding 44DF)
```

This saves/restores the CCR without touching any data register, and keeps the
stack word-aligned (the current byte push does not, which the m68k coding
conventions warn against).

Verified by assembling with `vasmm68k_mot -m68020` and by executing a CCR
round-trip under `qemu-system-m68k -M virt -cpu m68020` (a program that sets
C=1, pushes the CCR, clears the flags, pops the CCR back, then takes `if_cs`
prints the expected result).

### 3.2 IR generator change (`AssignmentGen.translate`)

Only when a call has **at least one status-flag return AND at least one
non-flag result** (register or float), reorder so the register/float returns are
materialized first, protected by a single `PUSHST`/`POPST`:

```
CALL
PUSHST                          ; save post-call status S0
[otherPairs: LOADHR + store]    ; read return registers before extraction;
                                ;   may clobber flags and scratch registers
POPST                           ; restore S0
[flagPairs: per-flag PUSHST/extract+store/POPST when >1 flag]
```

- Register/float values are safely in IR virtual registers before the flag
  extraction clobbers the scratch register.
- The float case (`LOADHFACZERO`/`LOADHFACONE`, which lowers to a `jsr` on 6502)
  is covered by the same outer save/restore.
- The existing per-flag `PUSHST`/`POPST` sandwich (for >1 status flag) is kept
  unchanged; this change is orthogonal.
- Flags-only and registers-only multi-assigns keep the current codegen (no
  extra instructions).

Pseudo-code in the extsub branch:

```kotlin
val hasStatusFlags = flagPairs.any { !isVoidTarget(it) }
val hasOtherResults = otherPairs.any { !isVoidTarget(it) }
if (hasStatusFlags && hasOtherResults) {
    result += IRCodeChunk(null, null).also { it += IRInstructions.simple(Opcode.PUSHST) }
    otherPairs.forEach(::processPair)
    result += IRCodeChunk(null, null).also { it += IRInstructions.simple(Opcode.POPST) }
    flagPairs.forEach { pair -> wrap-if-multi(pair) }
} else {
    flagPairs.forEach { pair -> wrap-if-multi(pair) }
    otherPairs.forEach(::processPair)
}
```

The "status flags MUST be processed first" comment in `AssignmentGen.kt:36` is
then no longer accurate; it becomes "status flags must be read before anything
that clobbers them, which is guaranteed by the surrounding PUSHST/POPST".

### 3.3 m68k backend change (`codeGenM68k/.../InstrControl.kt:180-200`)

Change `PUSHST`/`POPST` to the memory form and drop the `d0` round-trip (and the
now-unnecessary `invalidateD0Cache()` calls). The 68000 guard stays: the 68000
cannot save the CCR with a nonprivileged instruction.

No changes are needed in new6502, the VM, or the IR file format.

## 4. Alternatives considered

- **New IR opcodes `PUSHHR`/`POPHR`** (save/restore a hardware register slot)
  around the flag extraction. Works, and keeps `PUSHST`/`POPST` semantics
  unchanged, but requires IR enum changes, `IRTextCodec`/reader support,
  peephole side-effect sets, VM support, and implementations in both backends.
  More plumbing for the same result.
- **d0-preserving `PUSHST`** (push `d0` plus the CCR, restore both). Legal on
  all m68k CPUs, but leaves `d0` clobbered *between* `PUSHST` and `POPST`, so a
  `LOADHR d0` inside the wrap still reads garbage. Rejected.
- **6502-only reorder without save/restore.** On 6502 `LOADHR` (`sta`/`stx`/
  `sty`) is flag-neutral, so the reorder alone would work there, but the stores
  and the m68k `move`-based `LOADHR` are not, so it does not generalize.

## 5. Edge cases

- `void` targets: excluded from both the wrap condition and the per-pair
  handling, as today.
- Mixed status flag + `@A`/`@D0` + float returns: covered by the same outer
  save/restore.
- `amiga500` (68000): remains a hard codegen error, now triggered only for the
  flags-plus-register combination.
- Portability note: `move ccr,-(sp)` / `move (sp)+,ccr` are 68010+ instructions.
  The only non-68000 m68k target is `qemu68k` (68020), so this is safe today. If
  a 68010 target is ever added it still works; a 68000 target cannot use it at
  all (already an error).

## 6. Test plan

- **ksim65 regression** (`compiler/test/codegeneration/TestNewCodegenBugs.kt`):
  `-> bool @Pc, ubyte @A` returning 42, assert the byte survives; run with
  `newCodegen=true` and with the legacy codegen as the reference, for
  `optimize` false and true.
- **m68k**: a `TestM68k`-style test asserting the `move ccr,-(sp)` sandwich and
  that no `d0` round-trip remains around the extraction; if feasible, a
  `qemu68k` execution check.
- **VM**: an IR-text test asserting the reorder/save appears (asmsub bodies
  cannot be executed by the VM).

## 7. Affected files

- `codeGenIntermediate/src/prog8/codegen/intermediate/AssignmentGen.kt` - reorder
  + outer `PUSHST`/`POPST`.
- `codeGenM68k/src/prog8/codegen/m68k/InstrControl.kt` - register-neutral
  `PUSHST`/`POPST`.
- `compiler/test/codegeneration/TestNewCodegenBugs.kt`,
  `compiler/test/codegeneration/TestM68k.kt`,
  `compiler/test/vm/TestCompilerVirtual.kt` - tests.
