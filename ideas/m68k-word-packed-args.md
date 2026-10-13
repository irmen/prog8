# M68K word-packed argument slots

Status: **implemented** - and as the **default** convention, not opt-in. This document
was a design for an opt-in `%option word_packed_args`; per project decision the packed
layout shipped as the default for all m68k targets (IR format 8) with no option at all:

* The slot geometry lives with `IRProgram.kt` in the `intermediate` module as
  `incomingSlotSize` (no mode parameter; the separate displacement helper was dropped
  because all values are read from the slot base).
* `StackFrameLayout` accumulates slot widths right-to-left (suffix sums).
* `InstrControl` pushes per-width and pops the summed area size.
* `IRProgram` only requires an even `incomingSize`; the old
  `== parameters.size * 4` parity check is gone.
* `IR_FORMAT_VERSION`/`IR_FORMAT_MIN_SUPPORTED` are 8: old uniform-slot `.p8ir`
  files are rejected instead of silently miscompiled.
* Hand-written asm following the old convention was updated (stdlib `die()` in
  `amiga500/syslib.p8` and `amiga1200/syslib.p8`: `14(a5)` -> `12(a5)`).
* `technical.rst`, `todo.rst`, `m68k-frames-followups.md` and both coder skills
  were updated.

The sections below are kept as the original design record (option-related parts
marked as not applicable).

## Original design: opt-in `%option word_packed_args` (superseded)

## Goal

Halve the stack cost of narrow arguments (byte/bool, word/uword) in the M68K
all-stack calling convention, without silently breaking the current (v1) layout.

Non-goals:

* Changing the local-variable frame layout, `frameSize`, `frameVregSlots`, `link`/`unlk`.
* Changing result passing (still register/FPU-accumulator based).
* Changing `asmsub`/`extsub` register conventions.
* ~~Making this the default~~ (making it the default was later chosen after all;
  `TestCompilerOnExamples` turned out to have no m68k size assertions, so no
  example-size updates were needed).

## Current implementation (v1) - impact analysis

The convention is one uniform padded 4-byte slot per argument:

* byte right-justified at slot`+3`, word at slot`+2`, long/pointer/float at slot`+0`;
* parameter `i` of `N` at `8 + 4*(N-1-i)(a5)`;
* caller pushes left to right, then pops `N*4`; callee epilogue is unaffected.

### Data flow

1. `IRCodeGen` creates `IRSubroutine`s with parameters and (later) a `CallSite` per
   call whose arguments carry `CallLocation.ParameterMemory`.
2. `StackFrameLayout.apply()` (`codeGenIntermediate/.../StackFrameLayout.kt:57`)
   decides which subs are frameable, assigns parameter offsets/displacements, emits
   the copy-in prologue, sets `sub.incomingSize`, and calls
   `rewriteCallArgumentLocations` to turn each argument's
   `CallLocation.ParameterMemory` into `CallLocation.FrameSlot`.
3. The m68k backend (`codeGenM68k/.../InstrControl.kt`) pushes each `FrameSlot`
   argument into its own 4-byte slot and pops `count*4` after the call.
4. The `.p8ir` persists offsets as `frame:<n>` text and `INCOMING=<n>` on each SUB.

### Exact sites that hard-code the 4-byte slot

| Location | What it does |
|---|---|
| `StackFrameLayout.kt:44` | `PARAM_SLOT_SIZE = 4` |
| `StackFrameLayout.kt:443` | parameter offset `INCOMING_BASE + PARAM_SLOT_SIZE * (N-1-index)` |
| `StackFrameLayout.kt:446-450` | narrow right-justify displacement `3` / `2` / `0` |
| `StackFrameLayout.kt:669` | `sub.incomingSize = paramSlots.size * PARAM_SLOT_SIZE` |
| `IRProgram.kt:609` | `require(incomingSize % 4 == 0)` |
| `IRProgram.kt:610-612` | `require(incomingSize == parameters.size * 4)` |
| `codeGenM68k/InstrControl.kt:845-868` | push shape: byte `subq.l #4,sp` + `move.b ..,3(sp)`; word `subq.l #4,sp` + `move.w ..,2(sp)`; long `move.l ..,-(sp)` |
| `codeGenM68k/InstrControl.kt:678-687` | cleanup `pushedSlots * 4` then `addq`/`lea` |
| `IRFileReader.kt:134-198` | `OPTIONS` whitelist; `else -> throw IRParseException("illegal OPTION $name")` |
| `IRFileWriter.kt:286-310` | `OPTIONS` emit list |
| `docs/source/technical.rst:255-288` | documents the uniform 4-byte convention |

### Relevant invariants confirmed

* The caller-side type used to pick the push size is the **parameter's** IR type,
  `CallArgument`'s `paramDt = codeGen.irType(parameter.type)`
  (`codeGenIntermediate/.../ExpressionGen.kt` around the `Calls.argument(...)` call).
  So caller and callee already agree on the type; only the size mapping is missing.
* The m68k backend ignores the numeric `CallLocation.FrameSlot` offset; it only
  checks `is CallLocation.FrameSlot` and assumes push order = argument order
  (`InstrControl.kt:848`). The offsets only matter to the callee's prologue loads.
* `CallLocation.FrameSlot` requires `offset >= 8`; `AddressBase.FrameSlot` requires
  `offset != 0`. Both hold for word-packed offsets (even, starting at 8).
* Frames only exist on M68K targets, where `FLOAT_MEM_SIZE == POINTER_MEM_SIZE == 4`
  on every target (`Qemu68k`, `Amiga500`, `Amiga1200`). So a "wide" argument is always
  4 bytes in a frame.
* The VM rejects frame-based IR (`virtualmachine/.../VmProgramLoader.kt`), so there is
  no VM compatibility concern.
* `IRSubroutine`'s `incomingSize` require fires only at construction. From codegen it
  is constructed with `incomingSize == 0` and assigned the real value afterwards
  (`StackFrameLayout.kt:669`), so the equality invariant is effectively a **.p8ir
  read-time self-check**.

## Proposed design

### Convention (word-packed, opt-in)

* Sizes: `bool`/`byte`/`ubyte` -> 2-byte slot; `word`/`uword` -> 2-byte slot;
  `long`/`float`/pointer -> 4-byte slot.
* Alignment: every slot is 2-byte aligned; the argument area size is always even.
* Value placement within a slot: everything at `slot+0`. A byte pushed with the
  single-instruction `move.b ..,-(sp)` reserve lands on the slot's base (even)
  address, because the 68000 byte predecrement on A7 steps by 2 and stores at the
  slot base.
* The other byte of a 2-byte slot holding a byte value is **garbage** (undefined).
  A byte argument must be loaded byte-sized (`move.b`), never word-sized: a
  `move.w` from the slot would also read the garbage byte. The generated callee
  loads are byte-typed (LOADM BYTE at slot+displacement), so only hand-written
  asm can get this wrong; external asm must be written accordingly.
* Parameter offset: same recurrence, but the stride is now the previous parameter's
  slot size (suffix sum) rather than a constant 4.
* Caller pushes left to right into the natural-size slots and pops the exact area
  size (even).
* Callee epilogue (`unlk a5` / `rts`) is unchanged.

Worked example, `sub f(ubyte a, uword b, ulong c)`, args `($12,$3456,$89ABCDEF)`:

| | v1 (default) | word-packed |
|---|---|---|
| `c` | `8(a5)` (disp 0) | `8(a5)` (disp 0) |
| `b` | `14(a5)` (disp 2) | `12(a5)` (disp 0) |
| `a` | `19(a5)` (disp 3) | `15(a5)` (disp 0) |
| `incomingSize` | 12 | 8 |

v1 caller: `subq.l #4,sp`/`move.b #$12,3(sp)`; `subq.l #4,sp`/`move.w #$3456,2(sp)`;
`move.l #...,-(sp)`; `lea 12(sp),sp`.
word-packed caller: `move.b #$12,-(sp)`; `move.w #$3456,-(sp)`;
`move.l #...,-(sp)`; `addq.l #8,sp` (or `lea 8(sp),sp` when a status-flag result must
survive).

If all parameters are wide, the word-packed layout is byte-for-byte identical to v1
(same offsets, same pushes, same cleanup) - a useful property to assert in tests.

### Single source of truth for slot geometry

Add one helper so the layout and the backend cannot disagree. Place it in
`intermediate/src/prog8/intermediate/` (both `codeGenIntermediate` and `codeGenM68k`
depend on the `intermediate` module):

```kotlin
// abstract shape, exact signature to be settled
fun incomingSlotSize(irType: IRDataType, target: ICompilationTarget): Int =
    when (irType) {
        IRDataType.BYTE, IRDataType.WORD -> 2
        IRDataType.FLOAT -> target.FLOAT_MEM_SIZE.toInt()      // 4 on m68k
        else -> target.POINTER_MEM_SIZE.toInt()                // 4 on m68k
    }
```

A separate displacement helper was dropped: all values are read from the slot base
(the byte push stores there), so `paramDisplacements[name] = 0` everywhere.

Note `slotSizeFor` (`StackFrameLayout.kt:838`) is **not** reusable: it sizes *local*
cells (byte = 1) and serves a different purpose.

### Layout algorithm change

In `frameableSubroutine`, keep the per-parameter loop but accumulate from the last
parameter backwards:

```kotlin
var cursor = INCOMING_BASE
for (index in sub.parameters.indices.reversed()) {
    val elemDt = ...
    val slotSize = incomingSlotSize(irTypeFor(elemDt), target)
    paramSlots[scopedParamNames[index]] = cursor
    paramDisplacements[scopedParamNames[index]] = 0
    cursor += slotSize
}
val incomingSize = cursor - INCOMING_BASE
...
sub.incomingSize = incomingSize
```

The copy-in prologue already reads `paramSlots[...]` + `paramDisplacements[...]`
(`StackFrameLayout.kt` around line 440 / the `frameDirect` emission), so it needs no
separate change beyond using the new values.

### Backend change

`translateArgument` (`InstrControl.kt:844-869`) sizes each push from the helper:

* byte: `move.b <reg>,-(sp)` - the single-instruction push: on the stack pointer a
  byte predecrement steps by 2 (not 1), so it reserves the 2-byte slot in one go and
  the byte lands at the slot's base byte. The emitted line carries a
  `(note: pushed as a word)` comment so the hidden word-width reservation is not
  misread. Byte parameters therefore read with a zero within-slot displacement.
* word: `move.w <reg>,-(sp)` (a 2-byte predecrement; no `subq` needed)
* long/pointer: `move.l <reg>,-(sp)`
* float: `fmove.s <freg>,FP_ACC` + `fmove.s FP_ACC,-(sp)` (unchanged; 4-byte slot)

`translateCall` (`InstrControl.kt:676-688`) computes cleanup as the **sum of the
individual argument slot sizes** instead of `count * 4`. `addq.l #N,sp` still works
for any even `N` in 1..8; larger or status-flag cases keep `lea N(sp),sp`.

### Option and IR plumbing

Items 1-4 (the `%option`, `CompilationOptions` field, reader/writer round-trip) are
**not applicable** after the shipped-as-default decision: there is no option and no
`OPTIONS` entry. Item 5 was implemented in its simplest form - because there is no
mode anymore, the relaxed require is unconditional:

5. Relax the `.p8ir` self-check in `IRProgram.kt:609-612` to
   `require(incomingSize >= 0 && incomingSize % 2 == 0)` and drop the
   `== parameters.size * 4` equality entirely (no mode to condition it on; the
   layout-time and backend guards are now the validation, and a mode-free
   `IRSubroutine` cannot be stricter anyway). Instead of keeping a v1 parity
   check, `IR_FORMAT_VERSION` and `IR_FORMAT_MIN_SUPPORTED` were bumped to 8 so
   old uniform-slot `.p8ir` files fail loudly ("unsupported IR format: 7")
   rather than silently miscompiling - the opposite tradeoff from the opt-in
   design above, correct once the old layout exists nowhere in the compiler.

### Documentation

Through `docs/source/technical.rst:255-288` now documents the packed layout as **the**
convention (including the garbage upper byte of a byte's 2-byte slot and the addq/lea
cleanup rule); `docs/source/todo.rst:64` and `ideas/m68k-frames-followups.md` mark the
follow-up done. Both coder skills state the word-packed rule. (The original plan to
present v1 as the default is superseded.)

## Implementation plan

Phase A (prerequisite, one worker):
`incomingSlotSize` helper (in `intermediate`, shipped in `IRProgram.kt`) + invariant
relaxation + IR format bump (shipped; replaces the opt-in plumbing items 1-4 above).

Phase B and Phase C can then run in parallel:

* Phase B (layout): `StackFrameLayout` suffix-sum offsets/displacements and
  `incomingSize`.
* Phase C (backend): `InstrControl` per-argument push sizing and summed cleanup.

Phase D (parallel after A): docs + skill updates + hand-written-asm audit
(the stdlib `die()` subs were the only strand picks).

Phase E: tests (can be written alongside B/C, run at the end).

Final: `gradle build --console=plain` (touches `.kt` and stdlib `,asm`), then a manual
qemu68k/vamos run.

## Test plan

Unit / IR (`intermediate`):
* `IRSubroutine` accepts an even non-multiple-of-4 `incomingSize` and rejects an odd
  one; a mixed-width layout (e.g. `INCOMING=8` for 3 params) round-trips.
* Old formats below 8 are rejected by the reader.
* `IRTextCodec` round-trips packed `frame:<odd-or-even>` offsets (already generic).

Compiler end-to-end (`compiler/test`, no option needed - it is the default now,
no vasm needed):
* Mixed `sub f(ubyte, uword, ulong)`: assert per-param offsets/displacements and
  `INCOMING == 8`; `long @ +8`, `word @ +12`, `byte @ +14` (via the IR/`.p8ir`, not by
  string-dumping asm). Done in `TestStackFrameLocals`.
* Copy-in parameter case (parameter referenced from an inline-asm/defer site): the
  prologue load uses the packed offset (byte and word values are read from the slot
  base itself, so a single ubyte param loads from `8(a5)`).
* All-wide parameters: layout equals the v1 layout exactly (true by construction;
  not separately asserted).
* The existing `TestStackFrameLocals` expectations were updated to the packed values
  (`INCOMING=8` for mixed args, `INCOMING=12` for five args, etc.).
* Cross-check implemented in `StackFrameLayout.rewriteCallArgumentLocations`: the sum
  of the pushed argument slot sizes must equal the callee's `incomingSize`.

Backend emission (`codeGenM68k/test/TestStackFrameEmission.kt`, parse asm into lines,
`assemble = false`):
* first the `generateAsmWithIncoming` helper got an optional explicit-`params`
  argument (its default derivation is meaningless for packed slots); the existing
  uniform-slot tests keep using the old derivation unchanged.
* byte push = `move.b ..,-(sp)` (single instruction, A7 steps by 2, listing comment
  "(note: pushed as a word)"; the byte lands at the slot's base byte).
* word push = `move.w ..,-(sp)` (no `subq`).
* cleanup uses the summed even size (`addq.l #N,sp` when 1..8 and no status result,
  else `lea N(sp),sp`).
* status-flag result forces `lea` (CCR preserved).
All implemented in `TestStackFrameEmission`.

Regression: full `gradle build`. There are no m68k `TestCompilerOnExamples` size
assertions, and 6502/VM codegen is untouched, so example sizes (which are only
asserted for c64/cx16/pet32/virtual) do not change.

Manual verification (documented, not CI): a small program summing mixed-width
parameters, compiled `-target qemu68k`, run in the qemu68k setup; and a hand-written
asm caller following the packed snippet.

## Risks, edge cases, open questions

* Caller/callee disagreement if `CallArgument`'s type ever diverges from the
  parameter type. Mitigated by the shared helper plus the sum-equals-`incomingSize`
  test/check.
* The byte push stores at the slot's base (even) address: a 68000 byte predecrement
  on A7 steps by 2 while the byte itself is written at the decremented slot base. The
  callee therefore reads byte parameters from `slot+0`
  (see the backend change above).
* Single narrow argument cleanup `addq.l #2,sp`; status-flag results force `lea`.
* Displacement/offset ranges shrink, never grow - no new out-of-range risk.
* Hand-written asm documented in `technical.rst` must follow the (new) compiled
  convention; external callers of framed subs must push word-packed slots. The stdlib
  `die()` subs were the only affected hand-written code found.
* Resolved: no option at all - the packed layout shipped as the default (this
  replaces the original "option name / CLI flag / flip the default later" question).
* Alternative considered: store a per-`IRSubroutine` `wordPackedArgs` flag to keep the
  strong `parameters.size`-based invariant. Rejected as redundant with the
  program-level option; can be revisited if hand-edited `.p8ir` validation is wanted.
