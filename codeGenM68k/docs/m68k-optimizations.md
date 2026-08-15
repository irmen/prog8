M68k Codegen Performance Optimizations
========================================

The current M68k codegen has NO CPU register allocation. IR virtual registers
live in a memory "register file" (`p8_regfile`, in BSS). Every instruction
that touches a register does so through that memory slot, using D0-D2 and A0
as scratch and FP0-FP7 for floats.

This document lists low-hanging-fruit optimizations that do NOT require a
full register allocation pass. They are ordered roughly by impact and ease.

What is already good
---------------------

Some patterns already operate directly on the register-file memory slot and
should be left alone:

- NEG, INC, DEC (addq/subq/neg on memory), InstrArithmetic.kt
- ADD/SUB with immediate (addq for 1-8, else `add.x #imm, mem`)
- ADDIM/SUBIM against memory variables
- CMPI (`cmpi.x #imm, mem`, and `tst.x mem` for #0), InstrArithmetic.kt:330
- STOREZM / STOREIM zero via `clr.x mem`
- LOAD with immediate 0 via `clr.x mem` (InstrLoadStore.kt)
- LOADR (register copy) as a direct memory-to-memory `move.x mem, mem`
- register-to-register ALU: `move.x src,d0` + `op.x d0, mem`
- ANDI/ORI/EORI with immediate: `opi.x #imm, mem`
- register-source ANDM/ORM/XORM: `move.x reg,d0` + `op.x d0, mem`
- INV/INVM (bitwise not): `not.x mem`
- divide/multiply already take a memory operand where the instruction allows it
- LOAD with a labelSymbol as a direct `move.l #label+off, mem` (InstrLoadStore.kt)
- compare-and-branch immediates: `cmpi.x #imm, mem` / `tst.x mem` directly on
  the slot (InstrBranch.kt), matching `CMPI` in InstrArithmetic.kt:329
- bit ops (BITTST/BITSET/BITCLR/BITTOG) directly on the register-file slot:
  `bset #(bit % 8), mem + (size-1-bit/8)` (big-endian, so the target bit lives
  in byte `size-1-bit/8` at position `bit % 8`), a single instruction with no
  d0 round-trip (InstrBitwise.kt)
- memory-form shifts/rotates for `.w` count=1: `lsl.w mem`, `lsr.w mem`,
  `asr.w mem`, `roxl.w mem`, `roxr.w mem` directly on the regfile slot. The
  memory path (`memoryShiftRotate`, InstrBitwise.kt:287) already used this for
  explicit memory targets; the register-target path (`shiftRegister` and the
  four rotate functions, InstrBitwise.kt:251-405) now does the same, collapsing
  3 instructions into 1. Only `.w` count=1 is supported by the 68000 memory
  form; `.b`/`.l` and counts 2-8 keep the d0 round-trip.
- QEMU 68020 quirk workaround: the qemu68k target's 68020 model has a bug
  where `asr.w` with absolute addressing zero-extends the 16-bit operand
  before the shift instead of sign-extending it, giving a logical shift
  result for negative values. The 68000 (vamos), the `(a0)` addressing
  mode, and the register form are all unaffected. The codegen works around
  this on the qemu68k target only by emitting `lea slot,a0; op.w (a0)`
  instead of `op.w slot` for all `.w` count=1 memory-form shifts/rotates
  (InstrBitwise.kt, `emitMemoryWordShiftOrRotate`). The 68000 keeps the
  optimal single-instruction absolute form.
- byte multiply (InstrArithmetic.kt, `emitMulOp`): the `srcReg != null`
  case for `.b` no longer issues a dead `move.b dst,d1`; it loads src
  into d0, saves to d2, then loads dst into d0 (6 instructions instead
  of 7).
- d0 loads for byte/word consumers no longer zero-extend first: ADDR/SUBR
  read only the operand's own width and integer LOADX/STOREX/STOREZX read
  only `d0.w` via `(a0,d0.w)`, so the `moveq #0,d0` clear is skipped there.
  Float indexing keeps the clear because it reads the full `d0.l`. The former
  `loadRegToD0`/`loadIndexToD0` helpers were removed; the loads are emitted
  inline per call site.


1. Peephole "d0 cache" (biggest win, still no full allocation)
----------------------------------------------------------------

The scratch usage is very regular: D0-D2 data registers, A0 for addresses,
FP regs for floats. Most operations funnel through d0 as `move mem,d0`.

Add a tiny state machine to AsmGen that tracks "d0 currently holds virtual
register rX of type T". When the immediately preceding emitted instruction
left that slot in d0, skip the redundant `move mem,d0`.

Invalidation rules:
- any instruction that writes a register-file slot
- any instruction that clobbers d0 (nearly all ALU ops, calls, pushes)
- any branch target / basic-block boundary / chunk boundary
- any PUSH/POP (they may touch d0)

This removes a large fraction of the remaining register-file loads without
doing any real allocation. A second slot (d1) can be tracked similarly if
the first proves effective.

Correctness note: the M68k compares/branches depend on CCR flags, so the
cache must never skip a load that is followed by an instruction that relies
on the flags being set by that load. Since the cached loads are pure data
moves that do not set flags, only skip the load, never reorder anything.


Suggested order of implementation
-----------------------------------

1. Item 1: the peephole d0 cache, as a separate pass, with the invalidation
   rules above.

### Implementation Plan

**Approach: Pre-emission state tracking in AsmGen**

The m68k codegen emits text directly to a `StringBuilder` via `emitLine()`. There is no
intermediate instruction representation in the output, so optimization must happen *during*
emission (the IR peephole optimizer already runs before codegen). Add a cache state to
`AsmGen` that tracks which virtual register d0 currently holds, and provide helper methods
that check the cache before emitting loads.

**Cache state** (add to `AsmGen.kt`):
```kotlin
private var d0CacheReg: Int = -1        // virtual register number, -1 = empty
private var d0CacheType: IRDataType? = null
```

**Helper methods** (add to `AsmGen.kt`):
```kotlin
// Load virtual register into d0, skip if cache hit
fun emitLoadD0(reg: Int, type: IRDataType) {
    if (d0CacheReg == reg && d0CacheType == type) return
    emitLine("move${dtSuffix(type)}  ${regAddr(reg)}, d0")
    d0CacheReg = reg; d0CacheType = type
}

// Store d0 to virtual register, update cache
fun emitStoreD0(reg: Int, type: IRDataType) {
    emitLine("move${dtSuffix(type)}  d0, ${regAddr(reg)}")
    d0CacheReg = reg; d0CacheType = type
}

// Invalidate cache when d0 is clobbered
fun invalidateD0() { d0CacheReg = -1; d0CacheType = null }

// Invalidate cache when a virtual register slot is modified directly
fun invalidateSlot(reg: Int) { if (d0CacheReg == reg) invalidateD0() }
```

**Invalidation points** (where to call `invalidateD0()` or `invalidateSlot()`):
- `translateChunk()` start: `invalidateD0()` (new basic block)
- Any instruction that writes d0: nearly all ALU ops, `emitLoadD0` with miss,
  `moveq`, `lea`, `clr`, `ext`, `swap`, etc.
- Any instruction that writes a virtual register slot directly
  (e.g. `add.x #1, mem`, `clr.x mem`, `not.x mem`, memory-form shifts/rotates):
  call `invalidateSlot(reg)` for that slot
- `CALL`/`CALLI`/`CALLFAR`: `invalidateD0()` (d0 is caller-saved)
- `JUMP`, `BSTCC`, `BCC`/`BGT`/etc.: `invalidateD0()` (control flow)
- `PUSH`/`POP`: `invalidateD0()` (they touch d0)

**Files to modify:**
1. `AsmGen.kt` — add cache state, helpers, and `invalidateD0()` at chunk start
2. `InstrLoadStore.kt` — replace raw `move.x mem,d0` with `emitLoadD0()`,
   `move.x d0,mem` with `emitStoreD0()`; invalidate on memory writes
3. `InstrArithmetic.kt` — use `emitLoadD0`/`emitStoreD0`; invalidate after ALU
   ops that write d0; invalidate slots after memory-direct ops
4. `InstrBitwise.kt` — same pattern; invalidate after `and.x`/`or.x`/etc.
   write d0; invalidate slots after memory-direct bit ops
5. `InstrBranch.kt` — `invalidateD0()` before any branch
6. `InstrControl.kt` — `invalidateD0()` before calls and after returns

**Special patterns to handle:**

*Load-store roundtrip (most common):*
```kotlin
// move.x mem,d0; op.x d0; move.x d0,mem
emitLoadD0(reg, type)           // may skip if cached
emitLine("op.x  ...")           // clobbers d0
invalidateD0()                   // d0 no longer holds reg
emitStoreD0(reg, type)          // cache updates: d0 holds reg
```

*Store then immediate reload (the win):*
```kotlin
// move.x d0,mem; ...; move.x mem,d0  <- second load is redundant
emitStoreD0(reg1, type)         // cache: d0 holds reg1
// ... some instruction that doesn't touch d0 or reg1's slot ...
emitLoadD0(reg1, type)          // CACHE HIT - skips the move
```

*Memory-direct write (e.g. `add.x #1, mem`):*
```kotlin
emitLine("add.x  #1, ${regAddr(reg)}")
invalidateSlot(reg)             // reg's value changed without going through d0
```

*Call sequence:*
```kotlin
emitLine("move.l  ${regAddr(argReg)}, a0")  // or d0 for value params
invalidateD0()                              // call clobbers d0
emitLine("jsr  fnLabel")
// return value in d0 - cache now holds nothing, but d0 has the result
invalidateD0()                              // don't trust d0 across calls
```

*Chunk boundary / branch target:*
```kotlin
private fun translateChunk(chunk: IRCodeChunk) {
    invalidateD0()                            // new basic block
    emitSourceComment(chunk.sourceLinesPositions)
    for (insn in chunk.instructions) translateInstruction(insn)
}
```

*Correctness for CCR flags:*
The cache only skips `move.x mem,d0`, which is a pure data move that does NOT set CCR.
The subsequent ALU op (which DOES set CCR) is always emitted. So skipping the load
never affects flag-based decisions. This is the key invariant from the doc.

*Interaction with the width-aware loads above:*
Since the zero-extension was removed for byte/word consumers, the cache must
know that a cached `.b`/`.w` value in d0 has stale upper bits. A `.l` consumer
(e.g. float indexing reading `d0.l`) must therefore be treated as a cache miss
even if the register number matches, because the stale upper bits cannot be
trusted. Keep the type in the cache key (as above) and additionally make the
float `(0,a0,d0.l)` sites either call `invalidateD0()` before loading or never
use `emitLoadD0` for their own index load.

*What about d1/d2?*
d1 and d2 are used for word/long multiply (operand registers) and for some
intermediate values. They are NOT loaded from memory as frequently as d0, so
caching them is a smaller win. Start with d0 only; add d1 if it proves effective.

**Testing:**
1. Run existing test suite — no regressions
2. Compare instruction counts before/after on `examples/test.p8` and a few
   representative programs (look for ~10-20% reduction)
3. Verify generated asm manually for a few cases
4. Run QEMU/vamos to verify runtime correctness

**Risks:**
- *Invalidation bugs*: be conservative; invalidate aggressively when in doubt
- *Type mismatches*: cache tracks both register number and type
- *Subtle interactions*: the memory-form shifts/rotates modify a slot without
  going through d0, so `invalidateSlot(reg)` must be called there

**Expected results:**
- 10-20% instruction count reduction on typical programs
- 5-10% execution speed improvement
- No correctness regressions
- Minimal compile-time impact (simple integer comparisons)
