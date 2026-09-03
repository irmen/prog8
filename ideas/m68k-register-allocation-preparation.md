# IR Preparation for M68k Register Allocation

**Status: proposed, not implemented.** This is preparatory work for the
deferred hardware register allocator described in
`ideas/m68k-register-allocation.md`.

**Priority: high prerequisite for the hardware allocator, but not a standalone
user-visible feature.** The P0 items should be completed before implementing
physical register assignment. The P1 items can follow before the allocator is
enabled in production.

## Goal

Make the IR a complete, trustworthy input for register allocation. In
particular, the future allocator must be able to see every instruction,
understand every virtual-register read and write, recognize calls and opaque
code, and validate the control-flow/dataflow assumptions it relies on.

The preparation must not change generated code, the calling convention, or the
behavior of any existing backend. It should reduce risk and implementation
scope for the later allocator while remaining useful to current optimizers and
backends.

## Scope

This plan covers:

- Uniform recursive traversal of all IR chunks, including `IRLoopChunk` bodies.
- Centralized register use/definition information for every instruction.
- Explicit abstract effects for call-like instructions.
- Lightweight register class and addressing hints.
- An IR verifier focused on dataflow and allocator prerequisites.
- Recursive, allocation-aware instruction rewriting infrastructure.

This plan does not cover:

- Assigning virtual registers to D, A, or FP registers.
- Choosing or changing the m68k calling convention.
- Spilling, reload insertion, stack frames, or callee-saved prologues.
- Resetting the global `RegisterPool` per subroutine.
- Encoding physical m68k register numbers in generic IR.

## 1. Uniform IR Traversal (P0)

Several analyses currently iterate `IRCodeChunk`s directly. That silently
misses instructions nested inside `IRLoopChunk.body` unless each caller adds
special handling.

Add shared traversal helpers near `IRCodeChunkBase` or `IRSubroutine`, for
example:

```kotlin
fun IRSubroutine.forEachChunk(action: (IRCodeChunkBase) -> Unit)
fun IRSubroutine.forEachInstruction(action: (IRInstruction) -> Unit)
```

Traversal must recurse through nested loop chunks and preserve a stable,
deterministic order. It should visit inline assembly and binary chunks as
chunks, even though they do not contain ordinary IR instructions.

Migrate these consumers to the helpers where applicable:

- `RegisterPacker` register/type collection and rewriting.
- `IRProgram.registersUsed()` and related validation.
- IR serialization checks and statistics.
- Future liveness and verifier code.

Acceptance criteria:

- A register used only inside a loop body is found by every register-collection
  path.
- Nested `IRLoopChunk`s are handled without duplicated recursion code.
- Existing IR output and backend output remain unchanged.

## 2. Centralized Register Effects (P0)

The current allocator prototype derives reads and writes through its own
`getRegisterAccess()` logic. Other code derives related information separately,
which makes omissions likely as new instructions are added.

Give each `IRInstruction` a single authoritative description of its virtual
register effects, conceptually:

```kotlin
data class RegisterEffect(
    val reg: RegisterNum,
    val type: IRDataType,
    val kind: AccessKind   // READ, WRITE, or READ_WRITE
)

data class RegisterEffects(
    val effects: List<RegisterEffect>
)
```

The implementation must account for:

- `reg1`, `reg2`, and `reg3` according to the instruction format.
- `fpReg1` and `fpReg2`.
- Indexed address and index operands.
- Function-call arguments and return registers.
- Multi-byte extraction, concatenation, and conversion instructions.
- Status flags separately from virtual registers where relevant.

Keep type information available alongside the effects, either through a
separate query or an effect entry containing the `IRDataType`. Do not infer a
hardware register class here; that remains a backend concern.

Migrate `RegisterPacker` and register validation to this API. Add tests for
every instruction family that currently has special handling in
`getRegisterAccess()`. This migration validates the new API, but does not
re-enable the memory-slot packer in production;
its global-slot and call-clobber unsoundness remains out of scope for this plan.

Acceptance criteria:

- Liveness, register counting, and validation agree on every instruction's
  reads and writes.
- Adding a new register-bearing instruction requires updating one central
  effect definition and its tests.
- Existing IR generation and serialized IR remain compatible.
- `IRFileWriter` and `IRFileReader` round-trip any new metadata unchanged.

## 3. Abstract Call Effects (P0)

The IR identifies calls for control-flow purposes, but does not explicitly
describe their dataflow effect. A future allocator needs to recognize all
instructions that can cross a call boundary, including indirect, far, and
system calls.

Add abstract metadata, for example:

```kotlin
enum class CallEffect {
    None,
    Ordinary,
    Indirect,
    Far,
    Syscall,
    Opaque
}
```

The exact enum is open to implementation, but it should distinguish at least:

- Ordinary calls whose arguments and returns are described by
  `FunctionCallArgs`.
- Calls with opaque or target-defined clobbers.
- Inline assembly or other chunks that must act as an opaque barrier.

This metadata must not claim specific D/A/FP clobbers in generic IR. The m68k
backend can later map abstract call effects to its selected convention.

Use the metadata initially for analysis and verification only. Do not change
register allocation or generated assembly as part of this step.

Acceptance criteria:

- `CALL`, `CALLI`, `CALLFAR`, `CALLFARVB`, and `SYSCALL` are classified
  consistently.
- Call arguments are reads and call returns are writes in the centralized
  effects API.
- A verifier can identify values live across a call without knowing a target
  subroutine's implementation.

## 4. Register Class and Addressing Hints (P1)

The existing IR types already distinguish pointers, integers, and floating
values. Preserve that information in a small, backend-neutral form so the
m68k allocator does not need to reverse-engineer instruction operands.

Possible hints include:

```kotlin
enum class RegisterClassHint {
    Any,
    Data,
    Address,
    Floating
}
```

Use hints conservatively:

- Floating registers require `Floating`.
- Pointer and address operands prefer `Address`.
- Ordinary integer values prefer `Data`.
- Values that can legally use either class remain `Any`.

For indexed operations, expose operand roles such as base address, index, and
scalar value. Do not force an allocation decision or introduce m68k-specific
register numbers into the IR.

Acceptance criteria:

- `LOADX`, `STOREX`, and `STOREZX` expose enough information to identify their
  base and index operands.
- Floating operations cannot accidentally be treated as integer values.
- Existing backends ignore the hints and produce the same output.

## 5. IR Dataflow Verifier (P0)

Add an opt-in verifier, initially usable from tests and diagnostic compiler
paths. It should validate assumptions needed by liveness and allocation:

- Every referenced virtual register has a known type.
- Every register read has a valid definition or is a subroutine input.
- Register effects include nested loop bodies.
- Call arguments and return registers have valid descriptions.
- Multi-byte values are used consistently with their declared width.
- Control-flow labels and chunk successors are valid.
- Every loop body has a valid enclosing loop and termination structure.

The verifier should report the subroutine, chunk label, instruction index, and
register involved. It should not reject intentionally opaque inline assembly;
instead, it should record that the chunk is a conservative barrier.

Add regression cases for nested loops, conditional branches, early returns,
switch-like dispatch, recursion-shaped call graphs, and calls with hardware
argument slots.

## 6. Recursive Rewriting Support (P1)

The current rewriting path should be generalized so transformations apply to
instructions inside nested chunks as well as top-level chunks.

For this preparatory phase, the rewrite operation can remain a virtual-register
rename. Structure it so a later allocator can replace a virtual operand with a
physical location or a spill/reload sequence without adding separate loop
handling.

The rewrite API should preserve:

- Chunk labels and successor links.
- Loop trip counts and loop nesting.
- Inline assembly and binary chunks.
- Source or diagnostic locations, where present.

Add a test that renames a register used only in a nested loop and verifies that
all references, including loop-body references, are changed exactly once.

## 7. Suggested Implementation Order

1. **P0:** Add recursive traversal helpers and migrate register collection.
2. **P0:** Add centralized register effects and migrate `RegisterPacker`.
3. **P0:** Add abstract call-effect metadata and update liveness/verifier
   consumers.
4. **P0:** Add the opt-in dataflow verifier and enable it in IR-focused tests.
5. **P1:** Add recursive rewriting and tests for nested loops.
6. **P1:** Add register class and addressing hints.

Each step should be behavior-preserving for all current targets. The existing
memory-slot packer should remain disabled in production until its correctness
issues are separately resolved.

The P0 milestone is the minimum gate for starting the hardware allocator. The
P1 milestone should be completed before the allocator is used for optimized
production output, but can be developed incrementally alongside its prototype.

## 8. Testing and Migration

- Add unit tests for every new traversal/effect/verifier component before
  migrating existing consumers.
- Run `gradle build --console=plain` after each step; generated code for all
  targets must remain byte-identical where the new code is not enabled.
- Expose the verifier through a hidden compiler flag or test helper first;
  enable it by default only after it passes on the full test suite.
- Keep `RegisterPacker` disabled in production until the separate correctness
  issues in its global slot model are resolved.

## 9. Handoff to the Hardware Allocator

This preparation is complete when the future m68k allocator can consume:

- A complete recursive instruction stream.
- Authoritative read/write sets.
- Known call boundaries and opaque barriers.
- Type and class preferences.
- Verified CFG and dataflow assumptions.
- A rewrite API that does not special-case loop bodies.

The hardware allocator can then be implemented independently, starting with
integer data registers and no spilling, followed by callee-saved handling,
spilling, address registers, and FPU registers as described in
`ideas/m68k-register-allocation.md`.
