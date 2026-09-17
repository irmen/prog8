# Problems in the M68k Register-Allocation Design

This report records the correctness problems and internal contradictions found
in `ideas/m68k-register-allocation.md`. It focuses on issues that must be
resolved before Stage 2 can be implemented safely.

## 1. Translator scratch registers cannot simultaneously hold allocated values

The design assigns two incompatible roles to the same physical registers:

- Section 2.3 allows call-free virtual registers to be allocated to
  D0, D1, A0, A1, FP0, and FP1.
- The existing translators use D0, D1, A0, A1, FP0, and FP1 unconditionally as
  scratch registers while expanding individual IR instructions.
- Stage 1 later says that D0, D1, A0, and A1 never hold allocated virtual
  registers and proposes an assertion enforcing that rule. FP0 and FP1 are
  omitted from that assertion even though the backend explicitly defines them
  as its FPU scratch registers.

CALL liveness does not make this safe. A call-free virtual register allocated
to D0 can remain live while a later, unrelated IR instruction uses D0 as an
accumulator and destroys it. The same failure applies to the other integer,
address, and FPU scratch registers. A scratch temporary being confined to one
instruction expansion does not prevent it from overlapping a virtual
register's live range across that instruction.

The design must choose one of these models:

1. Reserve all six translator scratch registers permanently. The initial
   allocatable pools then become D2-D6, A2-A4, and FP2-FP7.
2. Make scratch use allocator-aware. Every instruction expansion must declare
   its physical uses and clobbers, acquire non-conflicting scratch registers,
   and preserve or relocate live allocated values where necessary.

The first model is substantially simpler and matches the stated Stage 1
approach. The pool sizes, pressure estimates, success metrics, and tests must
then stop counting D0, D1, A0, A1, FP0, and FP1 as allocatable registers.

## 2. D2 has contradictory calling-convention roles

The convention correctly classifies D2-D6 as callee-saved, but it also says
that the commonly used D0-D2 asmsub argument and return slots are all
caller-saved. D2 cannot be both.

Using D2 as an argument is possible only if a live allocator value currently
held in D2 is preserved or relocated before argument marshalling. Using D2 as
a return register or declaring `clobbers(D2)` defines or destroys its old
contents, so the caller must likewise preserve any unrelated live value.
These operations are call-site-specific and are not represented by the
document's rule that every CALL has the same effects.

The problem extends beyond D2. Existing Prog8 and Amiga APIs use fixed
registers throughout the nominally callee-saved and reserved sets. Examples
include:

- Standard-library asmsubs that declare D2-D6, A2, or A6 in `clobbers`.
- Amiga library calls with arguments in D2-D7 and A2-A5.
- `Supervisor(pointer userFunction @A5)`.
- `Alert(long alertNum @D7)`.
- Multi-value returns using D0-D3.

Rejecting callee-saved or reserved registers in `clobbers` would therefore
break existing standard-library code. External routines also cannot be changed
to preserve Prog8's internal allocator convention.

Fixed-register calls need call-site metadata containing:

- Physical registers read as arguments.
- Physical registers defined as results.
- Additional physical registers destroyed by the call.

The allocator must create interference or preservation moves for those
registers. Wrapper stubs are another option, especially when an external ABI
uses A5 or D7. The uniform CALL rule can remain valid for ordinary Prog8
subroutines, but it cannot describe arbitrary asmsub and extsub boundaries.

## 3. Reserved registers can be destroyed by argument marshalling

The clobber policy only discusses registers modified inside a called routine.
Loading an argument into a fixed hardware slot already overwrites that
register before the call.

This is critical for A5 and D7:

- Once A5 is the frame pointer, marshalling an `@A5` argument destroys access
  to the caller's frame.
- D7 is the active physical `repeat` counter, so marshalling an `@D7` argument
  can destroy the surrounding loop state.

Rejecting `clobbers(A5)` or `clobbers(D7)` does not address either case.
Calls using reserved argument or result slots need generated save, marshal,
call, and restore sequences, or dedicated ABI wrappers. The saved value must
be restored only after all return values and flags that depend on the call
have been captured.

## 4. Push/pop around a live range is not a general spill strategy

The preferred spill target is described as pushing and popping around a live
range. General live ranges are not necessarily nested in LIFO order. They can
cross branches, loops, calls, and multiple uses. Naive push/pop insertion can
therefore produce different stack depths at CFG joins or pop a different
value from the one expected.

Sound stack spilling requires one of these mechanisms:

1. Reserve a fixed-size spill area once in the subroutine prologue, keep SP
   stable throughout the body, and address each spill through a fixed SP
   displacement.
2. Implement the A5 frame model first and assign fixed frame slots.
3. Perform substantially more complex CFG-aware stack-slot scheduling with
   proven stack-depth consistency.

The first or second mechanism is appropriate for the initial allocator. BSS
spill slots remain a temporary non-reentrant fallback and must be globally
unique across callers, callees, and interruptible code.

## 5. The recursion claims exceed what the current memory model supports

Stack-based register saves and spills are per invocation, but ordinary Prog8
parameters and locals remain statically allocated. A recursive call overwrites
the caller's parameter and local storage. Register allocation alone therefore
does not make normal subroutines recursive or reentrant.

The design currently claims that recursion is handled by the uniform CALL rule
and includes a recursion test justified by stack-frame saves. That only proves
that allocator-owned register state survives; it does not make the program's
variables recursion-safe.

Until `m68k-stack-memory-model.md` is implemented, the document should:

- Keep normal Prog8 recursion explicitly unsupported.
- Limit allocator recursion tests to synthetic routines without static
  per-invocation state, or describe them strictly as register-preservation
  tests.
- Avoid presenting stack spills as sufficient for full recursion soundness.

## 6. The caller-saved spill test conflicts with whole-live-range allocation

Section 2.3 says a virtual register may use a caller-saved physical register
only when its entire live range contains no CALL. Under that rule, a value in a
caller-saved register can never require spilling around a call.

The test plan nevertheless requires a caller-saved register to be spilled
around a call. Supporting that test requires live-range splitting: one segment
may use a caller-saved register, then move to a callee-saved register or spill
slot across the call, and optionally move back afterward. Whole-vreg greedy
colouring does not provide this.

The design must explicitly choose between:

- No live-range splitting initially: call-crossing values are assigned only to
  callee-saved registers or permanent spill slots, and the caller-spill test is
  removed.
- Live-range splitting: add interval segmentation, boundary moves, and the
  corresponding CFG correctness requirements to Stage 2.

## 7. Address-register pressure is underestimated

The pressure analysis says short-lived pointer virtual registers flow through
A0 or A1 and therefore do not consume A2-A4. That is incompatible with making
A0 and A1 pure scratch.

A pointer produced by one IR instruction and consumed by a later instruction
must persist between those instructions. It must occupy an allocated address
register, a spill slot, or a deliberately precoloured and protected scratch
live range. Merely copying it through A0 during its eventual use does not store
the value between its definition and use.

Address-register demand must therefore be measured from actual IR liveness,
including pointer temporaries. Def-use folding could reduce this pressure
later, but it cannot be assumed by the initial allocator design.

## Required design decisions

Before Stage 2 is delegable, the design should explicitly settle these points:

1. Whether translator scratch registers are permanently reserved or managed by
   the allocator.
2. How fixed-register argument, return, and clobber constraints are represented
   per call site.
3. How A5 and D7 ABI slots are wrapped without destroying frame or loop state.
4. Which fixed-offset stack or frame mechanism stores spills.
5. Whether live-range splitting is in scope.
6. That recursion remains outside the allocator's guarantees until parameters
   and locals are frame-resident.
7. Pool-pressure measurements based on real IR live ranges rather than source
   usage assumptions.

Without these decisions, the proposed allocator can silently corrupt live
values even when its virtual-register liveness and interference graph are
otherwise correct.
