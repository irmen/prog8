# M68k Register Allocation and Calling Convention Design

This document describes the design for a **true register allocator** for the
m68k code generation backend (`codeGenM68k`). It supersedes the earlier
`register-packing.md` plan, which only compressed the *memory* register file
(`p8_regfile` BSS block) and never placed values in real CPU registers.

The goal here is different and bigger: keep virtual register (vreg) values in
the m68k's actual hardware registers (D0–D7, A0–A6, FP0–FP7) for as long as
possible, and only spill to memory under register pressure. This yields both
speed (no load/store round-trips) and smaller code, which is the entire point
of targeting a register-rich 32-bit CPU.

---

## 1. Current State (background)

The m68k backend today (`AsmGen.kt`) spills **every** virtual register to a
single flat BSS memory block:

- `regFileLayout` (`AsmGen.kt:80-92`) lays out **all** registers returned by
  `program.registersUsed().regsTypes` — program-wide — into one block labelled
  `p8_regfile`.
- `regAddr(reg)` returns `p8_regfile+offset`, so every IR instruction becomes a
  load from memory into D0/D1, compute, store result back to memory.
- `RegisterPool` (`codeGenIntermediate/RegisterPool.kt`) assigns globally
  unique, monotonically increasing register numbers; `nextRegister` is never
  reset per subroutine.
- The VM's register file is a fixed `Array(99999)` and is irrelevant here; only
  the m68k (and new6502) backends actually consume the IR regfile as memory.

This works and is correct, but it throws away the m68k's main advantage: real
registers. The design below keeps the correctness of the current scheme while
adding a proper allocator.

---

## 2. Calling Convention (the core)

### 2.1 No stack arguments

Prog8 already has a stack-free calling convention (see `AsmGen.kt:12-17`):

- **Normal subs:** the caller writes arguments into the callee's *parameter
  variables* (memory/BSS, possibly ZP) before the `jsr`; the callee reads them
  from there.
- **asmsub/extsub:** arguments go into fixed hardware slots via
  `CallingConventionSlot` (`InstrControl.kt:640`): slots 10..17 → D0..D7,
  18..24 → A0..A6, 25..32 → FP0..FP7. In practice asmsub args/returns use
  D0–D2 and FP0–FP1.
- **Returns:** come back through virtual registers mapped to the caller's
  result register.

Because arguments are already in memory (or in volatile hardware slots), a value
passed to a callee and still needed afterward is *already spilled* in its
parameter/parent variable. No stack-frame argument handling is needed, which
removes a whole class of complexity that a C-style ABI has.

### 2.2 No save/restore at the CALL

The `CALL` instruction itself does **no** register save/restore. It is a bare
`jsr`/`bsr`, exactly like today. The VM's `CallSiteContext`
(`VirtualMachine.kt:64`) already carries only the return address, so this
constraint is already satisfied by the runtime.

### 2.3 Register split (m68k SVR4-style)

The convention splits the 16 general registers and 8 FPU registers:

| Registers            | Class              | Saved by | Role |
|----------------------|--------------------|----------|------|
| D0, D1, A0, A1       | caller-saved (volatile) | caller (around call if live) | scratch, asmsub arg/return slots, temporaries |
| D2–D7, A2–A5         | callee-saved (preserved) | callee (prologue/epilogue) | values that survive calls |
| FP0, FP1             | caller-saved       | caller   | float scratch / return |
| FP2–FP7              | callee-saved       | callee   | float values that survive calls |
| A6                   | frame pointer (callee-saved) | callee | stack frame |
| A7                   | stack pointer      | —        | hardware stack |

Return-value locations: D0 (int/ptr), D0:D1 (long long), FP0 (float/double),
A0 (struct/array pointer return).

The asmsub argument/return slots (D0–D2, FP0–FP1) are deliberately in the
**caller-saved** set, matching their volatile nature.

### 2.4 What a CALL means for liveness

Because of the split, every `CALL` behaves uniformly and the allocator applies a
single rule regardless of the target subroutine:

- **CALL kills all caller-saved registers** (D0, D1, A0, A1, FP0, FP1). Any
  value the caller holds in a caller-saved register and needs after the call
  must either be spilled to memory before the call or kept in a callee-saved
  register instead.
- **CALL preserves all callee-saved registers** (D2–D7, A2–A5, FP2–FP7). The
  callee promises to save/restore any it uses, so the caller's values there
  survive the call untouched.

This uniform rule is what makes the next two sections work.

---

## 3. Liveness Analysis

### 3.1 Intraprocedural (required)

A standard per-subroutine register allocator needs correct liveness *within*
each subroutine:

1. Build a CFG of the subroutine's code chunks.
2. Compute liveness via gen/kill + iterative dataflow → `liveIn`/`liveOut` per
   chunk.
3. Walk instructions to build live intervals (register, start, end, type).
4. Build a conflict graph from overlapping live ranges and cross-type
   incompatibilities.
5. Greedy-colour intervals onto physical registers, respecting register
   classes (Section 4).

The liveness results drive three decisions:

- **Call-spill:** which vregs are live across a `CALL` → must be in
  callee-saved registers or spilled to memory before the call.
- **Callee-saved usage:** which callee-saved registers the subroutine itself
  uses → emit prologue saves / epilogue restores only for those.
- **Spilling:** when register pressure exceeds the available physical
  registers.

> Note: the disabled `RegisterPacker` already contains intraprocedural
> liveness infrastructure, but it had bugs on complex control flow (nested
> loops/conditionals, early returns — see the old TextElite failure). Any
> liveness implementation must be validated against those CFG shapes before
> use. Reusing and repairing the existing infrastructure is preferred over a
> from-scratch rewrite.

### 3.2 Interprocedural (NOT required)

No cross-subroutine liveness analysis is needed. The uniform calling
convention (Section 2.4) means the allocator treats every `CALL` identically:
kill caller-saved, preserve callee-saved. The caller does not need to know
which registers a callee clobbers, and the callee does not need to know its
callers' usage.

Consequences:

- **Indirect calls (`CALLI`) and recursion** are handled by the same uniform
  rule — no target knowledge, no call-graph walk required.
- Interprocedural analysis is only an *optimization*:
  - *Leaf-subroutine detection*: a subroutine containing no `CALL` has no
    callee that could clobber it, but it must still preserve callee-saved
    registers **for its own caller**, so leaf status does not remove the need
    to save callee-saved registers it uses. (It mainly avoids any additional
    defensive reasoning.)
  - *Shrink-wrapping*: defer callee-saved saves to only the paths that actually
    call, instead of the prologue. A later optimization, not needed for
    correctness.

This is a key simplification versus the old packer, which needed call-graph
propagation or depth ranges precisely because it operated on a flat shared
*memory* regfile with no per-call save mechanism.

---

## 4. Register Classes

The allocator is class-aware because the m68k has distinct register files:

- **Data registers (Dn):** integer arithmetic on byte/word/long values.
- **Address registers (An):** pointers and address arithmetic; prefer keeping
  pointers in A-registers so `(An)` / `(An)+` / `-(An)` / `(An,d0)` addressing
  modes are usable.
- **FPU registers (FPn):** `float`/`double` values.

Each class has its own interference graph (or a class-tagged unified graph),
sized by the available registers in that class (8 D, 8 A, 8 FP, minus the
fixed roles like A6/A7).

---

## 5. Per-subroutine vreg reuse

Virtual register numbers may be reused across subroutines (the `RegisterPool`
can reset its counter per subroutine). This is safe *because* no value is live
in a shared hardware register across a `CALL` (guaranteed by the convention in
Section 2.4): when subroutine B uses D5 and clobbers it, any caller-live value
was either already in memory or in a callee-saved register that B preserved.

Benefits:

- The `p8_regfile` BSS block (now only a spill area) shrinks to the
  worst-case single-subroutine spill set rather than the program-wide total.
- Fewer distinct vreg numbers simplifies analysis and the regfile layout.

Reusing numbers requires no extra machinery beyond the convention already
described.

---

## 6. Spilling

When register pressure exceeds the physical registers available in a class, the
allocator spills vregs to memory:

- **BSS regfile:** the existing `p8_regfile` block, now demoted from primary
  storage to a spill area.
- **System stack:** push/pop around the live range (standard for a true
  allocator; distinct from "stack arguments", which Prog8 does not use).
- Spill stores/loads are inserted by the allocator at definition/use points.

The callee-saved prologue save/restore (Section 7) is the spill mechanism for
values that must survive a call; it is *not* a `CALL`-time save/restore.

---

## 7. Prologue / Epilogue (callee-saved)

For each subroutine, the codegen emits:

- At entry: save the callee-saved registers the subroutine actually uses
  (e.g. `movem.l d2-d7/a2-a5, -(sp)`), determined intraprocedurally.
- At every exit (`rts`): restore them (`movem.l (sp)+, d2-d7/a2-a5`).

Only registers that are used are saved, keeping the cost minimal. This is the
standard, cheap mechanism that makes per-subroutine register reuse and
recursion sound — and it is precisely why the old packer's "save/restore is
expensive" objection (which assumed saving the whole memory regfile) does not
apply here: only a handful of hardware registers are involved.

---

## 8. Relationship to the (disabled) RegisterPacker

`codeGenIntermediate/RegisterPacker.kt` is a graph-coloring register allocator
— the same algorithm family as the true m68k allocator proposed here. It
already implements, per subroutine: CFG construction, intraprocedural liveness
(gen/kill fixed-point), live-interval building, interval merging, conflict-graph
construction, greedy coloring, and instruction rewriting. Its only production
call site (`IRCodeGen.generate()`) is commented out, so today it runs only under
`TestRegisterPacker`; its file header comment documents what it does in its
present form.

In its present form it is a **memory-slot** packer, not a hardware-register
allocator:

- It coalesces virtual registers with non-overlapping live ranges into *shared
  memory slots* in the flat `p8_regfile` BSS block.
- It assigns slots from a **single global slot pool** (`startSlot = maxReg + 1`,
  shared `globalSlotTypes`), because the regfile is one block shared by all
  subroutines.
- It never models `CALL` clobbering — there are no interference edges for
  caller/callee-saved registers at call sites. Packing a caller and its callee
  into the same slot lets the callee overwrite the caller's value. This is
  precisely the old plan's §4 "fundamentally unsound for a flat shared regfile"
  problem, and it is why the packer is disabled.

### 8.1 What is shared (the reusable core)

The true m68k allocator reuses the packer's machinery rather than rewriting it:

- CFG building (`buildCFG`).
- Intraprocedural liveness (`computeLiveness`) — the gen/kill iterative
  dataflow.
- Live-interval construction and merging (including the "always merge same
  register's intervals" fix so a value survives gaps between uses).
- Conflict-graph construction (`buildConflictGraph`).
- The greedy-color loop and instruction rewriting (`greedyColor`, `rewrite`).

These mechanics are correct and carry over directly.

### 8.2 What differs (the upgrade)

| Aspect            | RegisterPacker (present form)        | True m68k allocator |
|-------------------|--------------------------------------|---------------------|
| Color target      | open-ended **memory slots** in `p8_regfile` | scarce **hardware registers** D0–D7 / A0–A6 / FP0–FP7, split by class |
| Resource scarcity | none — `slot` just grows until free  | hard cap → must **spill** to memory when full |
| CALL handling     | ignored (no interference edges)      | `CALL` kills caller-saved, preserves callee-saved |
| Code emission     | only renumbers instructions          | also emits prologue/epilogue `movem` saves for callee-saved regs |
| Spilling          | never happens                        | inserted under register pressure |
| Inter-subroutine  | global slot pool, coordination to avoid collisions | fully independent per sub (convention guarantees soundness) |

### 8.3 Why the calling convention makes it simpler

The central contrast is **interprocedural vs intraprocedural soundness**:

- **Current RegisterPacker: soundness REQUIRES the full-program call tree.**
  Because the regfile is one flat memory block shared by every subroutine, two
  subroutines packed into the same slot number will clobber each other if one
  can call the other. To be sound, the packer must therefore reason
  interprocedurally — build the call graph and ensure a callee's slots never
  collide with any caller's live slots (the old plan's call-graph-aware
  allocation, §4(a)), or fall back to depth-range disjoint slots (§5), or
  save/restore around calls (§4(b)). All of these require analyzing the
  whole-program call tree. As written, the packer packs each sub independently
  into a shared global pool and is therefore unsound — precisely because this
  interprocedural reasoning is missing.

- **m68k allocator with the call convention: allocation is purely
  subroutine-by-subroutine.** Because the convention makes every `CALL` behave
  uniformly — kill caller-saved registers, preserve callee-saved registers via
  the callee's own prologue/epilogue — the allocator never needs to know which
  subroutine is the target. Each subroutine is colored in isolation onto the
  same fixed physical registers, and the result is sound by construction. No
  call graph, no call-tree propagation, no depth ranges. Indirect calls
  (`CALLI`) and recursion are handled identically (each invocation has its own
  stack frame for the prologue saves).

This removes the *hardest* complexity of the packer, which was never the
coloring itself but **cross-subroutine soundness**:

- **No global slot pool / collision coordination.** The packer needed
  `startSlot = maxReg + 1` plus a shared `globalSlotTypes` map so packed
  subroutines would not stomp each other in the flat regfile. The true allocator
  just maps each sub onto the *same fixed physical registers*; because no value
  is live in a shared register across a `CALL`, per-subroutine allocation is
  automatically sound and fully independent. No depth ranges, no call-graph
  propagation, no `skipRegs` juggling.
- **Per-subroutine vreg reuse comes for free.** The `RegisterPool` can be
  reset per subroutine; the convention guarantees no clobber.
- **Interprocedural analysis is optional, not required.** It can still be used
  for optimizations (leaf-sub detection to skip callee-saved saves,
  shrink-wrapping), but it is NOT needed for correctness — unlike the packer,
  where interprocedural reasoning is mandatory for soundness.

So the *allocation problem* becomes the textbook per-subroutine graph coloring
the packer already demonstrates — minus the globality that made the packer's
design convoluted and bug-prone.

### 8.4 What the true allocator adds that the packer dodged

It is not strictly fewer lines, because the true allocator takes on three things
the packer entirely avoided:

1. **Register classes.** Real m68k splits D / A / FP files (pointers prefer A,
   floats in FP). That requires class-aware coloring, more structure than the
   packer's single undifferentiated slot space (which only had a float-vs-int
   type-compatibility check).
2. **Spilling.** The packer never spills; it just grows `slot` until free. The
   true allocator has only ~16 general + 8 FP registers, so it *must* spill
   under pressure (pick a victim, insert store/load around the live range, to
   the stack or the BSS regfile).
3. **Prologue/epilogue emission.** The packer only renumbers instructions; the
   true allocator emits `movem` save/restore for the callee-saved registers a
   subroutine uses.

**Net:** the convention trades the packer's *worst* complexity (interprocedural
soundness, global coordination) for the *standard, well-understood* complexity
of class-aware coloring + spilling. The result is more tractable and far less
error-prone, simpler where it mattered most — just not literally fewer lines
once spilling and classes are accounted for.

Once a true allocator is operational, the memory-slot packer becomes redundant
for m68k (the regfile is only a spill area); on 6502, where "allocation" is
mostly zero-page placement, the packing mindset remains relevant (see below).

---

## 9. Relationship to the 6502 (new6502) backend

This document is **m68k-specific**. On the 6502 the situation differs:

- Only three real registers (A, X, Y), and only A does arithmetic; X/Y are
  index registers. There is no wide register file to exploit.
- The effective register file is **zero page** (256 bytes of fast RAM). A
  "register allocator" there means placing hot vregs into ZP (vs 4-cycle
  absolute `p8_regfile` access) plus transient A/X/Y use during expression
  evaluation — much closer to the old packer's memory-slot goal.
- The same calling convention (no stack args, no save/restore at CALL,
  caller/callee-saved split applied to A/X/Y and ZP scratch) applies, but the
  payoff is far smaller than on m68k.

A 6502-specific design is left to a separate document; the depth-range packer
approach from `register-packing.md` remains directly relevant there.

---

## 10. Implementation Outline

1. **Define convention constants** in the m68k backend: which registers are
   caller-saved vs callee-saved, return-value locations, asmsub arg slots.
2. **Implement/repair intraprocedural liveness** (reuse `RegisterPacker`
   machinery; validate on complex CFGs: nested loops, conditionals, early
   returns, switches).
3. **Class-aware greedy colouring**: map vregs → physical D/A/FP registers,
   treating `CALL` as killing caller-saved registers.
4. **Emit prologue/epilogue** `movem` saves for the callee-saved registers the
   subroutine uses.
5. **Spill under pressure** to the BSS regfile or the system stack.
6. **Update `AsmGen`** to emit register-to-register instructions (e.g.
   `add.l d1, d0`) for allocated vregs instead of `p8_regfile` memory
   load/store round-trips.
7. **Optionally reset `RegisterPool` per subroutine** to allow vreg reuse and
   shrink the spill area.

### Example (conceptual)

Current spill model:

```asm
    move.l  p8_regfile+0, d0
    move.l  p8_regfile+4, d1
    add.l   d1, d0
    move.l  d0, p8_regfile+8
```

With the allocator (values already resident in registers):

```asm
    add.l   d1, d0          ; both operands already in registers
```

Plus a subroutine prologue/epilogue for any callee-saved registers used:

```asm
sub_label:
    movem.l d2-d7/a2-a5, -(sp)    ; save used callee-saved regs
    ; ... body ...
    movem.l (sp)+, d2-d7/a2-a5    ; restore before return
    rts
```

---

## 11. Test Coverage

Unit tests (in `codeGenM68k` and/or reusing `TestRegisterPacker.kt` structure)
must cover:

- Simple non-overlapping vregs coalesced into one hardware register.
- Overlapping live ranges forced into different registers.
- Cross-chunk liveness (vreg live across multiple code chunks).
- Nested loops and conditionals (the old TextElite failure case).
- Early returns / multiple exit points (prologue/epilogue symmetry).
- Value live across a `CALL` kept in a callee-saved register or spilled.
- Caller-saved register correctly spilled around a call.
- Recursion (stack frame saves handle it).
- Indirect calls (`CALLI`) handled by the uniform convention.
- asmsub/extsub argument slots (D0–D2, FP0–FP1) and return values.
- Float values routed through FP registers.
- Pointers preferred into address registers.
