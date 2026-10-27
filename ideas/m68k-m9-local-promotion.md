# M9 - Local Promotion to Virtual Registers

## Overview

Promote non-escaping scalar locals and parameters to virtual registers before
`StackFrameLayout`. This is the largest remaining optimization opportunity on
the m68k branch.

**Status:** Not implemented. The prerequisites (register allocator as the default
code path) are now in place (Stage 4 of the register-allocation effort is
complete), so M9 is unblocked.

Related documents (historical; the first two were removed after their items landed):
- `m68k-compiler-optimizations.md` (removed) tracked F1, F4/M1 and other IR/AST items
- `m68k-asm-peepholes.md` (removed) originally carried the M9 "moved" item
- `m68k-register-allocation.md` (removed) carried the allocator design; the allocator
  is now the sole code path in `M68kCodeGenerator.kt`

## Background

Currently, scalar locals and parameters are stored in frame slots (A5-relative
memory) on the m68k target. Every read or write goes through memory. Promoting
them to virtual registers lets the allocator keep them in physical CPU registers,
eliminating most frame-slot traffic.

### Current state

- `StackFrameLayout` moves plain-data locals into A5-relative frame slots
  (negative offsets) and parameters into the incoming argument area (positive
  offsets).
- Virtual registers (vregs) are allocated to physical registers by
  `M68kRegisterAllocator`, which is now the sole code path (no flag or fallback).
- `frameVregSlots` in `AsmGen.kt` still maps spilled vregs to frame slots, so
  promoted locals that the allocator cannot keep in a physical register spill
  back into the frame. Promotion only pays off in conjunction with the allocator,
  which is now always on.

### Opportunity size

From the baseline scan (approximate):
- 1,843 loads from frame locals and parameters
- 193 loads from locals of static subroutines
- 334 frame reloads of a value stored earlier in the same chunk
- The loop counter case (`addq.b #1,-5(a5)` / `move.b -5(a5),d2`) is the same family

Removes: A1, A2 (peephole), C2 and most reload/copy chains.

## Eligibility

Reuse the `StackFrameLayout` checks. A local is eligible for promotion when ALL of:
- Not address-taken (`name !in addressTaken`)
- No inline-assembly reference (`!isLabelReferencedInAsm`)
- Not static-initialized (`initializationValue == null` or `Numeric`)
- Not explicitly aligned (`align == 0`)
- Not `@shared`
- Not zeropage-wished (`zpwish` not `REQUIRE`/`PREFER`)
- Plain scalar data: byte/word/long/pointer (not structs, not strings; arrays are
  frameable but are not promoted as a single vreg)

Additionally:
- Must not escape (address not taken, no asm reference)
- Must be in a framed subroutine (frameable)
- Static subroutines: their locals cannot be promoted (re-entrancy)

## Implementation plan

### Phase 1: IR-level promotion (m68k only initially)

1. **Identify eligible locals** in `StackFrameLayout` or a new pre-pass.
   For each framed subroutine, find scalar locals/params that meet eligibility.

2. **Replace frame-slot references with vregs.** Instead of rewriting the local
   to `AddressBase.FrameSlot`, allocate a new vreg number and replace all
   `STOREM`/`LOADM`/`STOREIM`/etc. referencing that local's frame slot with
   vreg operations (`STORE`/`LOAD`/`MOVE`).

3. **Handle initialization.** The local's zero-initialization (from
   `StatementReorderer` and `StackFrameLayout` prologue) becomes a `LOAD #0`
   into the vreg, or is handled by the allocator's spill initialization.

4. **Handle parameters.** Incoming parameters are in the caller-pushed area.
   Promote to vreg with an initial `LOADM` from the incoming slot (copy-in).

5. **Handle escapes.** If a local is promoted but later needs its address
   (e.g., passed to an asmsub), fall back to frame slot for that local.

### Phase 2: Allocator integration

The allocator already handles vregs. Promoted locals become additional vregs
in the subroutine's liveness analysis. The allocator assigns them to physical
registers or spills them to frame slots.

Spill slots for promoted locals need to be allocated in the frame. The existing
`allocateVregSlots` in `StackFrameLayout` can be reused.

### Phase 3: 6502 vs m68k decision

**Key question:** Should promotion be enabled for 6502 targets too?

Arguments for m68k only:
- m68k has 12 allocatable registers (D2-D6, A2-A4, FP2-FP7), so promoted
  locals are likely to stay in registers, yielding large savings.
- 6502 has only 3 registers (A, X, Y) and uses zeropage for "registers".
  Promoted locals would likely spill to zeropage, which may not be cheaper
  than frame slots.
- The vreg-to-frame-slot mapping on 6502 (`frameVregSlots`) means promotion
  alone doesn't reduce memory traffic.

Arguments for both:
- On 6502, if a local is used in a tight loop, keeping it in A/X/Y could
  help. But the allocator (RegisterPacker) is less sophisticated.
- IR-level promotion is target-independent; the backend decides residency.

**Recommendation:** Implement for m68k first. Measure the 6502 impact separately.
If 6502 shows no benefit (or regression), gate promotion to m68k only via
`options.compTarget.cpu.is68k`.

### Phase 4: Testing

- Unit tests in `TestM68kRegisterAllocator.kt` and `TestStackFrameEmission.kt`
- Full example compilation and size measurement
- VM verification (`-target virtual`) for correctness
- Emulator verification (`amiga500` via FS-UAE)

## Risks

1. **Re-entrancy:** Static subroutines cannot promote locals (they'd share
   storage across activations). Must detect and skip.
2. **Address-taking:** If a promoted local's address is needed later, the
   promotion must be undone or the local must stay in a frame slot.
3. **Assembler references:** Inline asm referencing the local by name requires
   the static cell to remain.
4. **Frame size:** Promoted locals that spill need frame slots, potentially
   increasing frame size.
5. **6502 impact:** Unknown; could be negative. Gate behind `is68k` initially.

## Estimated impact

Based on the baseline scan: 1,843 + 193 + 334 = 2,370 frame-slot accesses
that could potentially become register accesses. On m68k with the allocator,
most of these would stay in physical registers, eliminating the memory
round-trips.

Expected: significant reduction in both `.asm` size and binary size, plus
speed improvement from register-resident locals.

## Open questions

1. Should promotion be limited to m68k initially? (Recommendation: yes)
2. Should promotion get its own detailed implementation plan? (Recommendation: yes, this document)
3. How to handle locals that are promoted but later need their address?
4. Interaction with `@shared` and static subroutines?
