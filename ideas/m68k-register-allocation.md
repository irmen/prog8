# M68k Register Allocation and Calling Convention Design

**Status: design in progress — convention decided, allocator internals not yet fully decided.** Listed as "Deferred" in `docs/source/todo.rst`. The calling convention (§2), register classes (§4), spilling strategy (§5), prologue/epilogue (§6), and the Stage 0/1/3 execution work (§7) are decided and delegable. The Stage-2 allocator internals are **not yet fully decided** — see §7.1 Open Decision Points.

Related design: `m68k-stack-memory-model.md` (stack frames for locals; reserves
A5 as the future frame pointer and A6 for AmigaOS library bases — both are
reflected in the register split below).

This document describes the design for a **true register allocator** for the
m68k code generation backend (`codeGenM68k`).

## 0. Goals (in priority order)

1. **Values live in CPU registers.** Virtual-register values must reside in the
   m68k's actual hardware registers (D0–D6, A0–A4, FP0–FP7) for as long as they
   are live. The in-memory `p8_regfile` block is demoted to a spill-only area
   (and shrinks accordingly). Memory residence is the exception, not the
   default. This is the whole point of targeting a register-rich 32-bit CPU.
2. **Dramatically smaller and better assembly, as a *consequence* of goal 1.**
   When operands are already in registers, the load/compute/store round-trips
   disappear and register-to-register instructions (`add.l d1, d0`) fall out
   naturally. Smaller/faster code that is produced by bolting on post-hoc
   peepholes does not count toward this goal — it must emerge from the codegen
   structure itself.
3. **The backend becomes intrinsically register-oriented, not patched.** The
   allocator must be a first-class input to instruction selection, not a
   post-processing or peephole layer over memory-everywhere codegen (see §1.1).

Non-goals: matching a C ABI's generality (no stack arguments, no varargs),
squeezing the last cycle out of hot loops (later micro-work), and applying
this to the 6502 targets.

---

## 1. Current State (background)

The m68k backend today (`AsmGen.kt`) spills **every** virtual register to a
single flat BSS memory block:

- `regFileLayout` lays out **all** registers returned by
  `program.registersUsed().regsTypes` — program-wide — into one block labelled
  `p8_regfile`.
- `regAddr(reg)` returns `p8_regfile+offset`, so every IR instruction becomes a
  load from memory into D0/D1, compute, store result back to memory.
- `RegisterPool` (`codeGenIntermediate/RegisterPool.kt`) assigns globally
  unique, monotonically increasing register numbers; `nextRegister` is never
  reset per subroutine.
- The VM's register file is irrelevant here; only the m68k (and new6502)
  backends actually consume the IR regfile as memory.

This works and is correct, but it throws away the m68k's main advantage: real
registers.

### 1.1 Lesson from the first attempt: a bolt-on allocator does not work

A first implementation of this plan was tried and the result was underwhelming
on every axis that matters: fragmented and complicated compiler changes, only
marginally smaller output, and "allocation" that behaved like a
post-processing/peephole step rather than making codegen intrinsically
register-oriented. Root cause, and the constraint it imposes:

**The backend's instruction selection is hardwired to memory operands.** Every
IR instruction translator (`InstrArithmetic`, `InstrBitwise`, `InstrControl`,
`InstrLoadStore`, `InstrBranch`, `InstrSyscall`) emits each vreg as an
*absolute memory operand*. (Structured-IR status: translators now consume typed
`dest`/`srcA`/`memory`/`target`/`callSite` operands and memory lowering is
centralized in `AsmGen.resolveMemory()` — that part of Stage 1 is done. What
remains is the vreg side, still lowered via `regAddr()`/`floatRegFileAddr()`
to the `p8_regfile` spill area:)

```
INC   ->  addq.b #1, p8_regfile+N
ADDR  ->  (load src into d0)  add.b d0, p8_regfile+N
```

An absolute memory operand and a CPU-register operand are **different
instruction selections**, not different spellings of the same operand. An
allocator layered on top of this backend cannot turn `p8_regfile+N` into `d3`
by changing an address computation; it has to either (a) intercept inside
`regAddr()` and return a register name as a string, which breaks every site
that assumed a memory operand (address-of, byte sub-access via `regAddrByte`,
load-op-store fused patterns, inline-asm references), or (b) add post-hoc
peepholes that rewrite the memory-operand patterns the translators just
emitted. Both were tried; both produce the fragmented, low-yield result above,
and (b) is literally what the existing `AsmOptimizer` regfile peepholes already
do.

**Consequence: allocation must come *first*, structurally.** Instruction
selection must be made location-agnostic *before* any allocator exists
(Stage 1 in §7), so that "which physical register holds this vreg" is an input
to codegen, not a rewrite of its output.

Sections 2–6 describe the design; §7 gives the implementation order. Of those
stages, **Stage 0 (preparations) can be started immediately** — it depends only
on the convention decisions in §2, not on the allocator existing.

---

## 2. Calling Convention (the core)

### 2.1 No stack arguments

Prog8 already has a stack-free calling convention:

- **Normal subs:** the caller writes arguments into the callee's *parameter
  variables* (memory/BSS) before the `jsr`; the callee reads them from there.
- **asmsub/extsub:** arguments go into fixed hardware slots via
  `CallingConventionSlot`: slots 10..17 → D0..D7, 18..24 → A0..A6,
  25..32 → FP0..FP7. In practice asmsub args/returns use D0–D2 and FP0–FP1.

Because arguments are already in memory (or in volatile hardware slots), a
value passed to a callee and still needed afterward is *already spilled* in its
parameter variable. No stack-frame argument handling is needed.

**This memory-parameter convention is the *interim* convention, not the
permanent one.** `m68k-stack-memory-model.md` §6.1 will later replace it with a
register/stack hybrid in which parameters are passed in registers (and excess
on the stack) and copied into the callee's A5 frame. The allocator's design is
already compatible with that evolution — the register classes (§4), the pinned
CALL/return boundary (§2.7), and frame-slot spilling (§5) all carry over — but
a future reader should not treat §2.1 as fixed. When the stack model lands,
§2.1 is the section that changes: parameter passing moves from "caller writes
callee's memory variables" to "vregs marshalled into argument registers /
frame slots at the call boundary," which the allocator then models like any
other vreg→register assignment.

### 2.2 No save/restore at the CALL

The `CALL` instruction itself does **no** register save/restore. It is a bare
`jsr`/`bsr`, exactly like today.

### 2.3 Register split

| Registers            | Class              | Saved by | Role |
|----------------------|--------------------|----------|------|
| D0, D1, A0, A1       | caller-saved (volatile) | caller (around call if live) | scratch, asmsub arg/return slots, temporaries |
| D2–D6, A2–A4         | callee-saved (preserved) | callee (prologue/epilogue) | values that survive calls |
| FP0, FP1             | caller-saved       | caller   | float scratch / return |
| FP2–FP7              | callee-saved       | callee   | float values that survive calls |
| D7                   | reserved (not allocatable) | —  | physical `repeat` loop counter (§2.5) |
| A5                   | reserved (not allocatable) | —  | future frame pointer (§2.5) |
| A6                   | reserved (not allocatable) | —  | AmigaOS library-base register (§2.5) |
| A7                   | stack pointer      | —        | hardware stack |

Return-value locations are pinned at the CALL boundary (§2.7): D0 for integer
and pointer results (byte/word results occupy the low part of D0; Prog8 `long`
is 32-bit and fits D0), FP0 for float results (32-bit 68881 singles). Pointer
results return in D0, matching the backend's current default
(`translateReturnValue`); explicit A-register return slots remain possible for
asmsub via slot annotations.

The asmsub argument/return slots (D0–D2, FP0–FP1) are deliberately in the
**caller-saved** set, matching their volatile nature.

**This split is the industry-standard M68k convention, not a Prog8
invention.** The System V ABI and GCC/LLVM use exactly this division:
D0/D1/A0/A1 (and FP0/FP1) caller-saved scratch, D2–D7/A2–A6 (and FP2–FP7)
callee-saved. The only deliberate deviations are the reserved registers of
§2.5 (D7 loop counter, A5 frame pointer, A6 AmigaOS library base in place of
SVR4's A6-as-frame-pointer), which are target-specific necessities. Keep the
split as-is; do not make the caller-saved registers callee-saved and do not
abolish their scratch role — both would diverge from SVR4/GCC and add
complexity for no conformance benefit.

**D0, D1, A0, A1 are scratch for the translators, but the allocator MAY use
them for call-free vregs.** These four registers combine two roles: (a)
transient scratch *within* a single IR opcode's expansion (accumulator, index,
pointer, second operand), and (b) the CALL/return boundary (§2.7). Both roles
mean "never holds a value live across a `CALL`". That is the only hard
constraint. Within it, the allocator may *also* allocate a vreg to a
caller-saved register when that vreg's **entire live range contains no
`CALL`** — such a vreg never needs the callee to preserve the register, so it
enjoys the caller-saved "free to use, nobody saves it" property. This is
standard practice (GCC/LLVM allocate caller-saved registers to short-lived,
call-free values) and it widens the effective pool for the common case at no
convention cost. A vreg whose live range *does* cross a `CALL` is placed in a
callee-saved register (D2–D6, A2–A4, FP2–FP7) or spilled, per §2.4.

Keeping these registers scratch for the *translators* (independent of whether
the allocator uses them for call-free vregs) is a deliberate simplification,
not a limitation discovered later:

- It formalizes existing reality — the translators already grab d0/d1/a0
  blindly (`loadRegOrZeroExtendToD0`, `loadPointerToA0`, `loadIndexToD0`), and
  returns already go through d0. A scratch register used within one opcode
  never crosses a `CALL`, so it cannot conflict with an allocated vreg that
  also never crosses a `CALL`.
- It removes the need for any allocator-aware scratch reservation protocol: a
  translator may always *use* d0/d1/a0/a1 freely for a within-opcode temporary,
  because such a temporary never overlaps a call-crossing live range.
  Multi-scratch expansions (e.g. STOREX needing index+base+value
  simultaneously) are automatically safe.
- It gives class-mismatched indexed/indirect operands a uniform fallback: move
  the value to a0/a1 (pointer) or d0/d1 (index/accumulator) first.

The one correctness rule the allocator must enforce: **a vreg allocated to a
caller-saved register must be spilled or moved to a callee-saved register
before any `CALL` it is live across.** The allocator already models "CALL
kills caller-saved" (§2.4), so this is a placement constraint, not new
machinery.

The allocatable pool is therefore: 5 callee-saved data (D2–D6), 3 callee-saved
address (A2–A4), 6 callee-saved FP (FP2–FP7) — plus the caller-saved D0/D1/
A0/A1/FP0/FP1 usable only for call-free live ranges. Whether this is enough is
exactly the register-pressure question the Stage-2 success metrics must
measure on real programs. (Open contradiction: `m68k-reg-problems.md` §1 shows
translator scratch use of D0/D1/A0/A1/FP0/FP1 conflicts with allocating
call-free vregs there — Stage 2 must not size pools or pressure estimates on
the above until that choice, reserve scratch vs allocator-aware scratch, is
settled.)

### 2.4 What a CALL means for liveness

Because of the split, every `CALL` behaves uniformly and the allocator applies
a single rule regardless of the target subroutine:

- **CALL kills all caller-saved registers** (D0, D1, A0, A1, FP0, FP1). Any
  value the caller holds in a caller-saved register and needs after the call
  must either be spilled to memory before the call or kept in a callee-saved
  register instead.
- **CALL preserves all callee-saved registers** (D2–D6, A2–A4, FP2–FP7). The
  callee promises to save/restore any it uses, so the caller's values there
  survive the call untouched.

This uniform rule is what makes the rest of the design work: allocation is
purely subroutine-by-subroutine, with no call graph, no call-tree propagation,
and no cross-subroutine coordination. Indirect calls (`CALLI`) and recursion
are handled by the same rule.

### 2.5 Reserved registers (D7, A5, A6)

Three registers are excluded from the allocatable pool entirely, on both m68k
targets:

- **D7 — physical `repeat` loop counter.** The backend uses d7 as the loop
  counter for `repeat` loops, and backend helper routines called via `jsr` from
  generated code clobber d7 *without any IR-visible CALL* (see the note in
  `IRProgram.kt` near `usedRegisters`). The allocator cannot model these
  clobbers, so D7 stays reserved until the loop-counter mechanism is reworked
  to request a register from the allocator.
- **A5 — future frame pointer.** Reserved for the stack-frame memory model in
  `m68k-stack-memory-model.md` (`link a5,#-N` / `unlk a5`). Reserving it from
  the start avoids a flag day when frames land.
- **A6 — AmigaOS library-base register.** On amiga500 the backend loads a
  library base into A6 around every OS call (`move.l 4.w,a6` or
  `move.l sys.xxxBase,a6`, then `jsr Symbol(a6)`; see CALLFAR in
  `InstrControl.kt`). These writes have no IR-visible call edge, so an
  allocated value in A6 would be silently corrupted. qemu68k has no
  library-base convention, but A6 is excluded there too so both m68k targets
  share one allocation model.

Note this deliberately deviates from SVR4, which uses A6 as the frame pointer;
Prog8's m68k targets use A5 for that role instead, because A6 is spoken for by
the AmigaOS ABI.

Net allocatable set for vregs: 5 data registers (D2–D6), 3 address registers
(A2–A4), 8 FPU registers (FP0–FP7) — since D0/D1/A0/A1 are pure scratch (§2.3)
and D7/A5/A6 are reserved (above).

### 2.6 asmsub/extsub clobber policy

`CpuRegister` includes D0–D7/A0–A6/FP0–FP7, so a hand-written `asmsub` or
`extsub` can declare (or worse, fail to declare) clobbering a callee-saved or
reserved register. That would break the uniform CALL rule of §2.4.

Policy:

- **`clobbers(...)` may only name caller-saved registers** (D0, D1, A0, A1,
  FP0, FP1). Declaring a callee-saved or reserved register is a compile error;
  hand-written assembly that needs D2–D6/A2–A4/FP2–FP7 must save and restore
  them itself. (A looser variant — honoring the declared clobber set as extra
  interference at that call site — is possible but abandons the "every CALL is
  identical" simplification; only add it if a real use case demands it.)
- **Inline asmsubs** are pasted directly into the instruction stream, so the
  same rule applies to their bodies: no writes to callee-saved or reserved
  registers without restoring them.
- **Interrupt/exception handlers** (e.g. VertB handlers on amiga500) are
  entered asynchronously, not through a CALL: they must save and restore
  *every* register they touch, including caller-saved ones, because there is no
  call site where the interrupted code could have spilled. Handler entry stubs
  need their own prologue convention.

### 2.7 Return-value marshalling at the CALL boundary

With per-subroutine allocation the caller cannot know which physical register
the callee allocated to its result vreg, so the boundary is pinned:

- **Single scalar/pointer result:** the callee moves it into D0 before `rts`;
  the caller's allocator treats D0 as defined by the CALL and consumes or moves
  it from there. This matches the current backend default
  (`translateReturnValue`: non-float returns go through d0).
- **Single float result:** same, through FP0.
- **Multi-value returns:** unchanged from today (memory slots / LOADHR).
  Register-based multi-return is a later optimization.
- **asmsub/extsub:** unchanged — explicit slot annotations, restricted by §2.6
  to the caller-saved set.

### 2.8 The role of LOADHR / STOREHR under allocation

`LOADHR` / `STOREHR` are the IR's only mechanism for moving a vreg to or from a
**named hardware register** (slots 10..32 = D0–D7/A0–A6/FP0–FP7). They serve
two distinct purposes:

1. **asmsub/extsub argument and return marshalling** (semantic, required). The
   IR must be able to say "this argument goes in D0". These opcodes are the
   correct hook for the §2.1 / §2.6 boundary and are **not** removed.
2. **Spill/return scaffolding** (today: LOADHR out of a memory slot into D0 to
   pass an arg, STOREHR the result back to memory). This is where the current
   backend wastes memory round-trips.

Under the allocator, case 2 mostly collapses and case 1 becomes cheap, because
the *vreg side* of LOADHR/STOREHR must also resolve through `operand()`
(§7 Stage 1), not unconditionally through `p8_regfile`:

- `STOREHR rX, slot=D0` where rX is allocated to d0 → **elided (no-op)**.
- `STOREHR rX, slot=D0` where rX is allocated to d3 → `move.l d3, d0`.
- `LOADHR rX, slot=D0` where rX is allocated to d0 → **elided**.

So the design keeps the opcodes but makes most of them no-ops or single
register-to-register moves. (Deliberately *not* "get rid of LOADHR/STOREHR":
removing them would require expressing asmsub register binding outside the IR
operand model, a worse design.)

---

## 3. Liveness Analysis

### 3.1 Intraprocedural (required)

A standard per-subroutine register allocator needs correct liveness *within*
each subroutine. Structured-IR input (already available): derive gen/kill from
`IRInstruction.registerAccesses` with `OperandDirection` (USE/DEF/USE_DEF),
via the `uses`/`definitions` sets keyed on `VirtualRegister` — `IntReg` and
`FloatReg` are strictly separate (`r5 != fr5`), so no shared `RegisterNum`
maps. `registerAccesses` already aggregates the registers inside `memory`
(`Indexed`/`Indirect`), `target` (`Indirect` pointer) and `callSite`
(arguments/results/bank/pointer). Use `RegisterOperand.allocationHint`
(`PREFER_DATA`/`PREFER_ADDRESS`) as the class-constraint input for §4:

1. Build a CFG of the subroutine's code chunks.
2. Compute liveness via gen/kill + iterative dataflow → `liveIn`/`liveOut` per
   chunk.
3. Build the interference graph from simultaneous liveness: an edge between any
   two vregs that are live at the same point.
4. Greedy-colour vregs onto physical registers, respecting register classes
   (§4), and spill what doesn't fit (§5).

The liveness results drive three decisions:

- **Call-spill:** which vregs are live across a `CALL` → must be in
  callee-saved registers or spilled to memory before the call.
- **Callee-saved usage:** which callee-saved registers the subroutine itself
  uses → emit prologue saves / epilogue restores only for those (§6).
- **Spilling:** when register pressure exceeds the available physical
  registers.

The disabled `RegisterPacker` (`codeGenIntermediate/RegisterPacker.kt`)
contains analysis machinery that is worth reusing as a starting point. Each
component needs scrutiny before reuse:

**Likely reusable:**
- **gen/kill dataflow** — standard iterative fixed-point; converges correctly
  given a correct CFG.
- **Interval construction** — backward-scan per chunk.
- **Greedy coloring** — standard algorithm.
- **IR rewrite** — mechanical regnum replacement.

**Needs validation before reuse:**
- **CFG edge construction** (`buildCFG`) — must be validated against calls,
  early returns, fall-through, and jump-table dispatch. The dataflow
  converges correctly *given a correct CFG*, but whether the CFG itself
  captures all control-flow shapes correctly is unproven.

**Do not reuse:**
- **Interference model** — derives conflicts from instruction-level register
  aliasing and judges interval overlap against a flat concatenation of chunks
  in layout order. Layout order is not execution order once loops exist (a
  value live in a loop header overlaps values whose intervals lie "later" in
  layout order but execute on an earlier iteration). Replace with a
  liveness-derived interference graph: an edge between any two vregs that are
  simultaneously live at any program point.

Validate the new interference model against nested loops, conditionals, early
returns, and jump-table dispatch before trusting the allocator.

### 3.2 Interprocedural (NOT required)

No cross-subroutine liveness analysis is needed: the uniform calling convention
(§2.4) makes every `CALL` identical. Interprocedural analysis remains available
only as a later *optimization* (leaf-subroutine detection, shrink-wrapping of
callee-saved saves); it is never needed for correctness.

---

## 4. Register Classes

The allocator is class-aware because the m68k has distinct register files:

- **Data registers (Dn):** integer arithmetic on byte/word/long values.
- **Address registers (An):** pointers and address arithmetic; prefer keeping
  pointers in A-registers so `(An)` / `(An)+` / `-(An)` / `(An,d0)` addressing
  modes are usable.
- **FPU registers (FPn):** `float` values (32-bit 68881 singles).

Each class has its own interference graph (or a class-tagged unified graph),
sized by the available callee-saved registers in that class after removing the
reserved registers of §2.5: 5 D (D2–D6), 3 A (A2–A4), 6 FP (FP2–FP7). The
caller-saved D0/D1/A0/A1 and FP0/FP1 are additionally allocatable, but only for
vregs whose live range crosses no `CALL` (§2.3).

### 4.0 Expected demand per class (where the pressure actually is)

The three classes are not under equal pressure, and this shapes both the pool
sizing and what the empirical gate (§7.1 #6) must measure.

- **Data (D2–D6) is the bottleneck.** Every integer intermediate, array index,
  loop variable, and general arithmetic value competes for these 5 callee-saved
  registers (plus caller-saved D0/D1 for call-free ranges, §2.3). If any pool
  is too small, it is this one.
- **Address (A2–A4) is rarely pressured.** Two different "pointer" populations
  must not be conflated:
  - *Short-lived pointer temporaries* (very common): the POINTER-typed IR
    values produced by array indexing, struct field access, and `&var`
    (`ADDR .p`, `ADD .p +offset`, `LOADI .p`). These are computed, used once
    for a `(a0)` / `(a0,d0.w)` access, then dead. Per §2.3 they flow through
    the pure-scratch A0/A1 and never enter the allocatable A2–A4 pool, so their
    frequency is irrelevant to ADDRESS-pool sizing.
  - *Long-lived pointer variables* (uncommon in typical Prog8 code): a
    user-declared `^^type`/`pointer` value kept and dereferenced repeatedly is
    the only real candidate for an A2–A4 allocation (so `(a2)`/`(a2,d3.w)`
    works in place, §4.1). Most subroutines have zero or one of these, so 3
    address registers is comfortable headroom.
- **FPU (FP0–FP7) is likely over-provisioned.** Float usage in typical Prog8
  programs is rarer still than pointer usage, so 8 FPU registers will almost
  never be a constraint.

Consequence: the empirical gate (§7.1 #6) should focus its measurement on the
data pool, not the address or FP pools. See §7.1 #6.

### 4.1 Class requirements flow from translator to allocator

Addressing modes are class-locked by the hardware: the base of `(An, ...)` and
`(off, An)` must be an address register, the index of `(An, Dn.w)` must be a
data register. A vreg used as a pointer base must therefore be **constrained**
to the ADDRESS class, and one used as an array index to the DATA class, at
allocation time — otherwise the allocator will place it in the wrong class and
the translator must fall back to copying into scratch a0/a1/d0/d1, erasing the
in-place benefit. The translator communicates this to the allocator as a
per-operand class constraint; when the constraint is honored the addressing
mode is emitted with the vreg in place (`(a2, d3.w)`), and when it cannot be
(register pressure) the translator falls back to the always-free scratch
registers.

### 4.2 RegClass lives in codeGenM68k, not on CpuRegister

Register class is an allocator-internal concept and is deliberately **not**
added to the shared `CpuRegister` enum (`codeCore`). That enum's only roles are
the asmsub/extsub `clobbers` sets and the `RegisterOrPair.fromCpuRegister()`
slot mapping — a user-facing asmsub vocabulary shared by all backends,
including 6502 and VM which have no m68k class notion. The class of a register
is also trivially derivable from its identity, so there is nothing to store on
the enum. Instead, `codeGenM68k` defines its own:

```kotlin
enum class RegClass { DATA, ADDRESS, FPU }

fun regClassOf(r: CpuRegister): RegClass = when (r) {
    CpuRegister.D0..CpuRegister.D7 -> RegClass.DATA
    CpuRegister.A0..CpuRegister.A6 -> RegClass.ADDRESS
    CpuRegister.FP0..CpuRegister.FP7 -> RegClass.FPU
    else -> error("6502 register $r has no m68k class")
}
```

The allocator keeps its own richer physical-register representation (class +
free/allocated + spill slot) keyed off this. The one legitimate consumer of the
class function outside the allocator is the §2.6 clobber-policy check, which
operates on the existing `clobbers: Set<CpuRegister>` and rejects any
callee-saved or reserved register.

---

## 5. Spilling

When register pressure exceeds the physical registers available in a class, the
allocator spills vregs to memory, inserting stores/loads at definition/use
points. Three possible spill targets, with different soundness properties:

- **Hardware stack (preferred):** push/pop around the live range. Per-invocation
  storage, so recursion-safe with no cross-subroutine coordination.
- **Frame slots:** once the A5 frame model (`m68k-stack-memory-model.md`) lands,
  spills become frame-resident; its frame layout already budgets "compiler
  spill slots". Same soundness as stack spills, with cheaper addressing
  (`move.l -8(a5),d0` instead of push/pop pairs).
- **BSS regfile:** the existing `p8_regfile` block, demoted to a spill area.
  Static storage, so **not reentrant** (sound today only because Prog8 has no
  recursion) and **shared by every subroutine**: a caller spilling its r5 to
  offset X collides with a callee spilling its own r5 to the same offset. BSS
  spill slots therefore have to be program-wide unique, which reintroduces the
  globality the convention otherwise avoids. Interrupt handlers must also never
  share BSS spill slots with the code they can interrupt.

**Backend helper calls must be allocator-visible.** The backend emits several
`bsr` calls to runtime helpers that have *no IR-visible CALL edge* — the
longmath routines (`p8_udivmod32`, `p8_sdivmod32`, `p8_umult32`, `p8_smult32`
in `amiga500/m68k_prog8_longmath.asm`), `math._sqrt_*`, `prog8_lib.strcmp`.
These take arguments in and return values in d0/d1 (caller-saved scratch), so
under the convention each is a call that kills D0/D1/A0/A1/FP0/FP1. Every such
emitted `bsr` must be modeled in the allocator as a call boundary — otherwise
the allocator will hand a live value to d0 and the helper will silently eat it.
The helpers themselves need no changes beyond honoring the §2.6 clobber policy
(and the d7 reservation, §2.5); no new IR-layer helper infrastructure is
required. The callee-saved prologue/epilogue (§6) is emitted inline per
subroutine by `AsmGen`, not as a shared helper routine you `bsr` to.

The callee-saved prologue save/restore (§6) is the spill mechanism for values
that must survive a call; it is *not* a `CALL`-time save/restore.

Recursion soundness has two halves: callee-saved prologue saves are already
per-invocation, but spills must be stack/frame-based too before recursion is
fully sound.

Per-subroutine vreg-number reuse (resetting `RegisterPool` per subroutine) is a
safe optional extra *because* no value is live in a shared hardware register
across a `CALL` — but the associated spill-area shrink only applies to
stack/frame spills, per the BSS caveat above.

---

## 6. Prologue / Epilogue (callee-saved)

For each subroutine, the codegen emits:

- At entry: save the callee-saved registers the subroutine actually uses
  (e.g. `movem.l d2-d6/a2-a4, -(sp)`), determined intraprocedurally.
- At every exit (`rts`): restore them (`movem.l (sp)+, d2-d6/a2-a4`).

Only registers that are used are saved, keeping the cost minimal. The reserved
registers (D7, A5, A6) never appear in the save mask by construction — the
allocator never hands them out. A subroutine that uses callee-saved FPU
registers gets a matching `fmovem fp2-fpN,-(sp)` / `fmovem (sp)+,fp2-fpN` pair.

---

## 7. Implementation Outline

The order below is load-bearing. The failed first attempt (§1.1) implemented
the *allocator* first and tried to retrofit the codegen onto it. That is
backwards: instruction selection must become location-agnostic **first**, so
that "where does this vreg live" is an input to codegen, not a rewrite of its
output.

### Stage 0 — preparations (independently mergeable, no behavior change)

These steps de-risk Stages 1–2 and can be done before any allocator exists.
Each is small, contained, and verifiable on its own.

1. **Catalog every `bsr`/`jsr` the backend emits and flag those with no IR
   call edge.** *Goal:* produce the definitive list of hidden call boundaries
   (the longmath helpers, `math._sqrt_*`, `prog8_lib.strcmp`, startup routines)
   that the allocator must treat as killing caller-saved registers (§5). Pure
   survey work; turns a class of "allocator silently corrupts a live value"
   bugs into a known, enumerated set.
2. **Build the byte-identical-output test harness.** *Goal:* a corpus of
   `.p8ir` → asm where any diff fails the test. This is the Stage-1 gate; having
   it ready first means Stage 1 (the large, mechanical refactor) is reviewable
   the moment it lands, rather than trusting it blindly.
3. **Introduce the scratch-register reservation API on `AsmGen`** and migrate
   the scattered ad hoc d0/d1/a0 `emitLine` references to it, keeping output
   byte-identical. *Goal:* remove the single biggest source of
   allocator-vs-handwritten-code register conflicts *before* the allocator is
   added, so Stage 2 doesn't have to chase clobbers through the translators.
4. **Make the helper call sites convention-clean.** *Goal:* give each runtime
   helper a declared, caller-saved-only clobber set per §2.6 (and stop the
   ad hoc d7 clobbering noted in §2.5). Contained change that removes the
   "d7 clobbered regardless of IR state" special case and makes the helpers
   ordinary citizens of the convention.
5. **Survey the reach of `ImmediateCallOptimization`.** *Goal:* know how large
   the Stage-2 step-9 decision (reimplement on allocator liveness vs restrict
   to spilled operands) actually is, so it can be scheduled rather than
   discovered late.

### Stage 1 — location-agnostic instruction selection (prerequisite; the bulk of the work)

Replace every use of `regAddr(reg)` / `floatRegFileAddr(reg)` in *operand
position* with a single indirection on `AsmGen` that takes the typed operand
(no `Int` vreg numbers — int/float are distinct `VirtualRegister`s):

```kotlin
fun operand(op: RegisterOperand): String         // "d3" if allocated, "p8_regfile+N" if spilled
fun storeOperand(op: RegisterOperand): String    // same, for destinations
```

(`fpOperand()` is subsumed: a `FloatReg` operand routes to `fpN` /
`p8_fregfile+N` via the same lookup.) Memory operands already go through the
centralized `resolveMemory(MemoryReference)` — only the vreg side still needs
this indirection.

All translator call sites then emit e.g. `add${s} d0, ${operand(dst)}`
without knowing or caring where the value resides. Initially the mapping is
empty and every query returns the regfile address — **stage 1 is behavior
neutral by construction**, which is exactly what makes it safe to land and
review on its own. Notes:

- The sites that genuinely require memory (address-of `&`, byte sub-access via
  `regAddrByte`, inline-asm chunk references to `p8_regfile`) keep lowering
  through the regfile path directly. These define the "must be spilled" set
  for the allocator essentially for free: any vreg used in such a position
  (not identified by a call count) must be memory-resident at that point.
- The vreg side of `LOADHR`/`STOREHR` also goes through `operand()` so they
  degrade to register moves or no-ops under allocation (§2.8).
- Model `CALL` boundaries through `CallSite.effects`
  (`CallEffects.memoryEffect`/`statusEffect`) plus the `callSite` register
  accesses (arguments/results), not via ad-hoc per-opcode kill lists.

**Indexed/indirect opcodes are a separate, non-mechanical sub-task.** Most
translators fit the "swap `regAddr` for `operand()`" pattern, but a minority do
not, because they *select* addressing modes around hardcoded scratch registers
rather than merely naming an operand:

- `LOADX` / `STOREX` use indexed addressing `(a0,d0.w*scale)`: the index vreg
  is forced into d0 via `loadIndexToD0` and the base into a0. Under allocation
  the index should resolve to its assigned register (`(a0,d3.w)`) and the scale
  handling (`muls.w #scale, d0`, `add.w d0,d0`, `lsl.w #2, d0`) must emit
  against that register, not hardcoded d0.
- `LOADI` / `STOREI` / `STOREZX` force the pointer vreg into a0 via
  `loadPointerToA0` and then use `(off,a0)`. Under allocation a pointer vreg
  resident in a2 should become `(off,a2)` directly.
- The float variants of these (`emitFloatMemBinary`, `emitFloatMemUnary`, etc.)
  have the same structure around `p8_fregfile` + a0.

For these, the §1.1 lesson ("different instruction selection, not different
operand spelling") applies *within* Stage 1 itself: the translator must pick
the addressing mode from the allocated register class (index register → Dn,
pointer → An), which is genuine per-opcode re-selection, not a string swap.
Treat them as a distinct work item with their own tests; do not assume the
blanket `operand()` refactor covers them.

**Scratch registers need only enforcement, not a reservation protocol.**
Because D0/D1/A0/A1 are decided to be pure scratch that never holds an
allocated vreg (§2.3), Stage 1 does *not* need an allocator-aware
acquire/release system. The work is reduced to: (a) route the existing helpers
(`loadRegOrZeroExtendToD0`, `loadPointerToA0`, `loadIndexToD0`) and the
scattered `emitLine` references to d0/d1/a0/a1 through named constants
(`SCRATCH_D0`, etc.) so there is a single place that owns the scratch set; and
(b) add a debug-time assertion that the allocator never assigns a vreg to any
of D0/D1/A0/A1/D7/A5/A6. This keeps the byte-identical checkpoint easily
attainable and removes what would otherwise be the largest hidden design item
in Stage 1. The one thing translators must *not* do is introduce a new
hardcoded scratch register outside the D0/D1/A0/A1 set.

- **Checkpoint before starting Stage 2:** with an empty allocation map, the
  generated assembly must be byte-identical to the pre-change output for a
  corpus of test programs.

### Stage 2 — the allocator decides the mapping, per subroutine

1. **Define convention constants** in the m68k backend: the caller-saved vs
   callee-saved split, the reserved set (§2.5), return-value locations (§2.7),
   asmsub arg slots.
2. **Implement/repair intraprocedural liveness and interference** (§3.1: reuse
    the packer's gen/kill dataflow, interval construction, and greedy coloring;
    validate CFG edge construction against nested loops, conditionals, early
    returns, and jump tables; replace the interference model with a
    liveness-derived one).
3. **Class-aware greedy colouring**: map vregs → physical D/A/FP registers,
   treating `CALL` as killing caller-saved registers.
4. **Wire the mapping into `operand()` / `storeOperand()`** (typed
   `RegisterOperand` lookup; float vregs resolve through the same entry point).
   Register-to-register code (`add.l d1, d0`) now falls out of the existing
   translators with no per-opcode special cases — this is the payoff of
   Stage 1.
5. **Emit prologue/epilogue** `movem` saves for the callee-saved registers the
   subroutine uses (§6).
6. **Spill under pressure**, preferably to the hardware stack (§5).
7. **Pin the return-value boundary (§2.7):** callee moves results to D0/FP0
   before `rts`; the caller consumes them from there.
8. **Enforce the asmsub/extsub clobber policy (§2.6).**
 9. **Restrict `ImmediateCallOptimization` to spilled operands** (`AsmGen.kt`).
    This pass does cross-instruction immediate forwarding into calls by
    reasoning about which regfile slots are dead — a second, overlapping
    liveness analysis that does not go through `operand()`. Leaving it
    untouched guarantees it conflicts with the allocator. **Decision (taken):
    restrict it to spilled operands** — it only applies to vregs the allocator
    has spilled (those that actually live in `p8_regfile`/stack); register-
    allocated arguments are handled by the allocator's own call-boundary
    marshalling instead. This keeps the pass (and the inline-memcopy path that
    depends on it) working during allocator bring-up with minimal change. The
    eventual goal is to delete it entirely: when the register-parameter calling
    convention lands (§2.1 evolution), fold immediate-forwarding into the
    call-boundary marshalling on allocator liveness (Stage 3 teardown).
 10. **Optionally reset `RegisterPool` per subroutine** to allow vreg reuse
     (§5).

### 7.1 Open Decision Points (not yet fully decided — needed before Stage 2 is delegable)

Stages 0, 1, and 3 are decided and can be handed to an agent as-is. Stage 2's
*internals* still have open design questions where an agent would otherwise be
designing rather than executing. These must be decided before Stage 2 is
delegable:

1. **`operand()` / `storeOperand()` width semantics.** The signature is shown
   (`fun operand(op: RegisterOperand): String`) but the width-marshalling is unspecified:
   when a vreg is register-resident but used at a different width (byte vreg in
   a word op), does `operand()` zero/sign-extend, and into *which* register?
   Today `loadRegOrZeroExtendToD0` does this against d0; under allocation the
   extension has to target a defined register. This is the most-encountered
   decision across the translator call sites.
2. **The class-constraint mechanism (§4.1 says *what*, not *how*).** We know
   index→DATA and pointer→ADDRESS, but not how the translator *communicates*
   the constraint to the allocator: a per-vreg constraint recorded in a
   pre-pass, a hint at allocation-call time, or an IR-level annotation. The
   constraint-propagation plumbing is undesigned.
3. **Spill-slot allocation policy (§5 lists targets, not policy).** How spill
   slots are assigned per subroutine, whether spill code is inserted during
   coloring (rewriting the IR) or emitted at use points, and the push/pop vs
   frame-slot *mechanism* given the A5 frame model does not yet exist.
4. **How liveness models CALL interference and the scratch registers
   concretely.** "CALL kills D0/D1/A0/A1/FP0/FP1" must be realized as kill
   edges at call points; and since D0/D1/A0/A1 are never vreg-allocated
   (§2.3), decide whether they appear in the interference graph at all or are
   handled purely by CALL-boundary marshalling.
 5. **Fate of `ImmediateCallOptimization` (Stage 2 step 9) — DECIDED.**
    Restrict it to spilled operands for allocator bring-up; delete it and fold
    immediate-forwarding into call-boundary marshalling when the
    register-parameter convention lands. See Stage 2 step 9.
 6. **Empirical validation gate (human judgment, stays open by design).**
     "Are the per-class pools big enough?" (§2.3 cost) and "does liveness
     survive programs with complex control flow?" (§3.1) are hypotheses to
     *measure*, not tasks. Per the demand analysis (§4.0) the measurement
     should focus on the **data pool (D2–D6)** — the address pool (A2–A4) and
     FP pool (FP0–FP7) are expected to have ample headroom because long-lived
     pointer and float variables are uncommon in typical Prog8 code. An agent
     can run the measurement, but a human judges the result and decides whether
     the pool is adequate. Note the caller-saved D0/D1/A0/A1 are already
     allocatable for call-free live ranges (§2.3), which relieves data-register
     pressure for the common case; the measurement determines whether that
     suffices or whether the convention itself needs revisiting.

### Stage 3 — teardown of the old mitigation layer

Much of `AsmOptimizer` exists only to undo what memory-everywhere codegen
produces (spill-then-reload bounces, immediate forwarding that matches literal
`p8_regfile+N` operand strings — see the many `REGFILE-DEPENDENT` comments and
`TestInstructionSelectionOptimizations`). Once Stage 2 lands:

- Delete the peepholes that the allocator subsumes rather than adapting them.
  Keeping both is how the first attempt became "fragmented and complicated".
- The instruction-selection optimizations that remain meaningful (e.g. constant
  folding into `moveq`, addressing-mode choices for true memory operands) stay,
  but must match against `operand()` output, not literal regfile strings.
- Expect heavy churn in `TestInstructionSelectionOptimizations.kt`, which has
  ~100 tests asserting exact `p8_regfile` instruction sequences. Rewrite these
  to assert properties (allocated vregs never appear as `p8_regfile` operands;
  specific sequences for known-small examples) instead of full listings.

### Success metrics (tie back to §0)

For a set of representative programs (the m68k examples, and real-world programs with complex control flow):

- Fraction of vreg operand references that resolve to CPU registers vs
  `p8_regfile` — should be the large majority; regfile BSS size should shrink
  to a small spill area. Caveat: because multi-value returns stay memory-based
  (§2.7) and asmsub marshalling keeps a foot in the regfile (§2.8), the regfile
  remains load-bearing for programs that use those features; the "small spill
  area" target applies to single-return, low-asmsub programs.
- Total instruction count and executable hunk size — expect a large reduction
  from eliminated load/store round-trips, *without* any peephole pass enabled.
- Code shape review: generated subroutines should read as register-to-register
  code with prologue/epilogue, not as regfile memory ops with patches.

---

## 8. Test Coverage

First, the Stage-1 gate (byte-identical output with an empty allocation map).
Then, unit tests (in `codeGenM68k` and/or reusing `TestRegisterPacker.kt`
structure) covering:

- Simple non-overlapping vregs coalesced into one hardware register.
- Overlapping live ranges forced into different registers.
- Cross-chunk liveness (vreg live across multiple code chunks).
- Nested loops and conditionals (layout-order vs execution-order intervals).
- Early returns / multiple exit points (prologue/epilogue symmetry).
- Value live across a `CALL` kept in a callee-saved register or spilled.
- Caller-saved register correctly spilled around a call.
- Recursion (stack frame saves handle it).
- Indirect calls (`CALLI`) handled by the uniform convention.
- asmsub/extsub argument slots (D0–D2, FP0–FP1) and return values.
- Float values routed through FP registers.
- Pointers preferred into address registers.
- Reserved registers (D7, A5, A6) never allocated, even under extreme register
  pressure.
- Values live in callee-saved registers across a `repeat` loop survive the
  loop's use of d7.
- An asmsub declaring a callee-saved or reserved register in `clobbers(...)`
  is rejected at compile time.
- Return value marshalled through D0/FP0 across a call, including when the
  caller's destination vreg is allocated to a different physical register.
- Caller and callee both spilling in a call chain: stack/frame spills do not
  collide; BSS spill slots are program-wide unique.
- Interrupt handlers save/restore every register they touch.
