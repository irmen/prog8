# M68K Frames Follow-ups

**Status: v1 implemented** on `m68k-stack` (uniform all-stack convention, caller cleanup).
User-facing description: `docs/source/technical.rst` ("M68K memory model and calling convention").
Implementation: `StackFrameLayout`, `AddressBase.FrameSlot` / `CallLocation.FrameSlot`,
`IRSubroutine.frameSize/incomingSize/frameVregSlots`. Decision: the VM does not get
activation records (see `docs/source/todo.rst`, "Won't do: VM stack frames"); it keeps
rejecting frame-based IR with an explicit error.

This file preserves the convention-level rationale from the deleted
`ideas/m68k-stack-memory-model.md` (§6.1/§6.4) so future optimizations stay variants
of v1, never second conventions.

## Why v1 is uniform 4-byte slots

- v1 follows the strict System V m68k scheme: every argument gets one padded longword slot,
  narrow values right-justified (`move.b x,3(sp)` / `move.w x,2(sp)` / `move.l x,(sp)`),
  param `i` of `N` at `8+4*(N-i)(a5)`, caller pops `N*4` after `jsr`, callee epilogue is
  always `unlk a5` / `rts` regardless of arity.
- Rejected for the baseline: all-register (struct-by-value params have no register form),
  hybrid first-scalar-in-D0/first-pointer-in-A0 (needs the register allocator to pay off;
  without it, it just swaps `move.l x,-(sp)` for `move.l x,d0` plus clobber-ordering hazards
  and mandatory copy-ins, doubling marshalling paths and tests).
- `asmsub`/`extsub` keep their explicit register-annotation ABI as a separate lane; normal
  subs calling them (or vice versa) follow §6.3 push rules. A Linux/GCC-style word-packed
  scheme (natural sizes, 16-bit alignment) is legal on 68000 but was deferred to keep v1
  offsets type-independent and hand-written caller rules trivial.

## Follow-ups (each needs its own design review + test pass)

### 1. Word-packed argument slots

Linux/GCC scheme: byte/bool = 2 bytes incl. pad, word = 2, long/float/pointer = 4,
still even-aligned. Saves stack space and bus traffic per narrow arg. Cost: frame layout
sums per-arg rounded sizes instead of constant stride; documented `asmsub` push rules change.
Introduce as a convention variant/flag, never by silently breaking v1 layout.

### 2. Register argument passing

Paired caller/callee specialization over the fixed stack convention (e.g. first scalar
in D0, first pointer in A0, rest on stack), applied only where it measurably wins (small
scalar signatures). Requires the register allocator (`ideas/m68k-register-allocation.md`)
first.

### 3. Reserved outgoing-argument area

Allocate the max call-site arg size once in the caller's `link` frame, write args via
negative A5 offsets, drop per-call `addq/lea` cleanup. Best for calls in loops; cleaner
for the allocator (no sp churn between spills). Caller frames grow; frame-size limits
must count the area.

### 4. Recursion-sound defers via framebase-as-parameter

Push (id, framebase) pairs on the defer handler stack, pass the parent's bottom-of-frame
address as a hidden trailing parameter, lower parent-local accesses to indirect
framebase-relative accesses. Lifts the current rejection of recursive deferring subs.
Also needs VM activation records in linear memory so saved frame bases are addresses.

### 5. Prologue zero-store coalescing

Merge per-element zeroing of clean locals into widest aligned stores (prototype: 17-byte
range in 5 stores instead of 17). Only clean non-initialized locals may merge; never
across dirty/initialized neighbours; floats excluded. Needs explicit per-slot
size/alignment/init bookkeeping in the layout pass.

### 6. Loop-aware liveness for frame slot reuse

Today vreg frame slots are shared only when live ranges provably don't overlap, and any
back edge (loop, backward branch, cross-sub jump) falls back to one slot per register.
Lifting needs real backward live-in/live-out dataflow over the CFG; interval extension
alone is unsound.
