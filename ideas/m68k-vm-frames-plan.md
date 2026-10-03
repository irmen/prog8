# VM Stack Frame Support (slice 4) - Implementation Plan

**Status: planned, not implemented.** Deliberately deferred by the user (2026-10-03);
the VM keeps rejecting frame-based IR for now. This document records the design and
the exact steps so the slice can be picked up later without re-deriving it.

Context: `ideas/m68k-stack-memory-model.md` (§9 "VM Changes", §17.4 item 4 "VM
activation records + recursion tests through the VM"). The m68k backend produces
frame-based IR; the VM refuses it.

---

## 1. Goal

Let the VM execute frame-based IR: one activation record per call, allocated on
call and discarded on return, with frame-relative memory operands resolved against
the active record. The payoff is that recursion becomes testable without qemu, and
the VM acts as a second, independent executor for the same IR (an m68k-produced
`.p8ir` can be run by `prog8c -vm`).

The VM must implement the *abstract* frame-slot semantics only. It does not emulate
A5, `link`/`unlk`, or any m68k instruction.

Out of scope for this slice: emitting frames for `-target virtual` (open decision,
§6), copy-in of constrained parameters, escape warnings.

---

## 2. Current state (verified)

Loader (`virtualmachine/src/prog8/vm/VmProgramLoader.kt`):

- `rejectFrameBasedIr()` (`:340-356`) is called from `load()` (`:22`) and throws
  `IRParseException` for: a subroutine with `frameSize != 0` (`:342`), one with
  `incomingSize != 0` (`:345`), a memory operand whose base is
  `AddressBase.FrameSlot` (`:348-350`), and a call argument whose location is
  `CallLocation.FrameSlot` (`:351-354`). This rejection is the §9-sanctioned
  interim behavior and must stay until this slice is done.
- Symbol resolution (`pass2replaceLabelsByProgIndex` `:370`, `resolvedSymbolInstruction`
  `:258`) folds every symbolic base into `AddressBase.Absolute` with the
  displacement folded in. It keys off `instr.memory?.symbolName`, which is `null`
  for frame references, so **frame-relative operands are already left untouched by
  this pass** - no change needed there.
- Call arguments: for `CallLocation.ParameterMemory` with `address == null`, the
  loader looks up the callee's parameter variable address (`:413-419`). A
  `CallLocation.FrameSlot` argument has no name and must simply be skipped here
  (it carries its own offset).
- Subroutine metadata is kept in `subroutines` (`:203`, keyed by
  `SymbolNames.stripPrefixes(label)`), which is what the machine needs to answer
  "how big is the frame of the subroutine I am calling".
- The final sanity check (`:227-233`) only asserts that no *symbol* references
  remain, so frame references pass it.
- `VmVariableAllocator` hands out variable addresses from 0 upwards and exposes
  `freeMem` = the first address above all variables, slabs and struct instances
  (`VmVariableAllocator.kt:6-9,45`). `VMTarget.RAM_SIZE` is 16 MB
  (`VMTarget.kt:25`), so there is plenty of room for a stack area.

Machine (`virtualmachine/src/prog8/vm/VirtualMachine.kt`):

- `InsCALL` (`:852-869`) writes every argument into the callee parameter variable
  memory (`requireNotNull(location.address)`), pushes a
  `CallSiteContext(returnChunk, returnIndex, callSite)` (`:64`) and branches.
  Anything other than `CallLocation.ParameterMemory` throws today.
- `InsRETURN` (`:871`), `InsRETURNI` (`:882`), `InsRETURNR` (`:944`) pop the
  `callStack` and restore `pcChunk`/`pcIndex`. `exit(0)` happens when the
  `callStack` empties.
- Branch/call targets stay symbolic: `branchTo` (`:238`) resolves
  `CodeReference.Label` through `irProgram.resolveCodeTarget`, so at CALL time the
  machine knows the callee *label* and can therefore look up its frame info.
- `Registers` (`Registers.kt`) stores virtual registers in two flat Kotlin arrays
  (`Array(99999)` of `Int`, plus `Array(99999)` of `Double`), with
  `getUB/setUB`, `getUW/setUW`, `getSL/setSL`, `getFloat/setFloat` accessors.
  `reset()` zeroes them.

---

## 3. The catch: activation records alone are not enough

Because the VM keeps every virtual register in one flat array shared by all
activations, a recursive subroutine that keeps an intermediate value in a vreg
would corrupt it exactly like the m68k backend did before slice 3. Frames on the
m68k side fix that by relocating those vregs into the frame
(`IRSubroutine.frameVregSlots`, see m68k-stack-memory-model.md §17.7).

So the VM must honor `frameVregSlots` too:

- On CALL to a framed subroutine, spill every register listed in its
  `frameVregSlots` (with its `IRDataType`) into that activation record.
- On RETURN, restore them.

This is the same trade-off §3 of the design doc describes ("spilled to frame slots
around calls"). Alternatives considered and rejected:

- Giving each activation its own full register file: 100000 entries per activation,
  hopeless for deep recursion.
- Spilling all registers on every call: far too slow.
- Ignoring `frameVregSlots`: silently wrong for exactly the recursive programs this
  slice exists to test.

Note that vreg numbers are program-wide unique and are not shared between
subroutines, so the caller's and callee's register sets are disjoint. That makes
the spill/restore set well defined, and it also means the return value register
(always a caller's vreg on this calling convention) is never part of the callee's
spill set.

Order matters on return: read the return value first, then restore the spilled
registers and tear the frame down (otherwise a spilled register could overwrite the
just-delivered result).

---

## 4. Design

### 4.1 Activation record layout

Mirror the hardware convention exactly, so the same IR runs on both executors and
any divergence in the §6.1 convention shows up immediately instead of being papered
over by a shifted base:

```
 block start                                          block start + frameSize + 8 = base
 |                                                    |                            |
 +-- locals (frameSize) --+-- 8 bytes linkage --+-- incoming args (incomingSize) --+
   base-frameSize .. base-1   saved a5 + return     base+8 .. base+8+incomingSize-1
```

- The 8 linkage bytes correspond to the saved A5 and return address that `link`
  puts at `0(a5)` and `4(a5)`. The VM does not use them; it reserves them only so
  that `8(a5)` really is the first argument slot, matching
  `INCOMING_BASE = 8` in `StackFrameLayout`.
- `FrameSlot(-k)` resolves to `base - k`, `FrameSlot(+k)` to `base + k`. Local slots
  are negative, incoming slots start at 8, which is why `CallLocation.FrameSlot`
  requires `offset >= 8`.
- Records come from a bump pointer starting at `VmVariableAllocator.freeMem`,
  reset in the same place the VM resets memory and registers. A simple
  stack-discipline bump pointer is enough: frames are strictly nested.
- Guard the total against `VMTarget.RAM_SIZE` and report a clear
  "VM stack overflow: N bytes of activation records" error rather than letting an
  index-out-of-bounds surface. This is the only new failure mode recursion adds.

### 4.2 Machine state

```
private var frameBase: UInt = 0u          // base of the active activation record
private var framePointer: UInt = 0u       // next free byte for a new record
private var frameStackSize = 0            // bytes currently in use, for the overflow message
```

plus one saved `frameBase` (and the record's start address) per `callStack` entry.
`CallSiteContext` (`VirtualMachine.kt:64`) is the natural place to store that, since
RETURN/RETURNR/RETURNI already pop it.

### 4.3 Address resolution

Add frame resolution next to the existing address computation used by
`loadm`/`storem`/etc.: for `MemoryReference.Direct` and `.Indexed` with an
`AddressBase.FrameSlot` base, compute `frameBase + slotOffset + displacement`
(plus `index * scale` for `Indexed`) instead of treating the base as a symbol.
`Indirect` over a frame slot needs no special case as long as the pointer value is
already computed.

The m68k backend emits a bounds check on the 16-bit displacement range; the VM
should sanity check that the computed address is inside the active record, so a
layout bug produces a clear message instead of a silent cross-activation access.

### 4.4 Calls

`InsCALL` (`:852`) becomes:

1. Resolve the callee's frame info from the call target label (frameless and
   unknown labels behave exactly as today).
2. Read all argument source values *first*, while the caller's `frameBase` is
   still active (a source vreg may live in the caller's frame).
3. If the callee has a frame: allocate the record, set `frameBase`, spill the
   callee's `frameVregSlots` registers into it.
4. Write the arguments:
   - `CallLocation.FrameSlot(offset)` -> `newBase + offset`, with the same
     byte/word/long/float widths the machine already uses in `InsCALL`, so the
     right-justified narrow slots work unchanged.
   - `CallLocation.ParameterMemory` -> unchanged (static convention).
5. Push the context (return chunk/index, call site, saved frame base, record start)
   and branch.

Returns: after delivering the result (`RETURN` has none, `RETURNI`/`RETURNR` write
`callSite.results`), pop the frame base, restore the spilled registers, and rewind
the frame pointer to the record start.

`exit()` from deep inside recursion does not need unwinding: the whole frame area
is reclaimed on the next `reset()`.

### 4.5 Loader changes

- Narrow `rejectFrameBasedIr` (`:340`) instead of deleting it wholesale: keep
  rejecting whatever the VM genuinely cannot execute, so a future gap produces an
  error rather than a miscompile. Frame *memory* operands need no rejection once
  §4.3 is in place.
- Skip `CallLocation.FrameSlot` arguments in the parameter-address fixup (`:413-419`).
- Hand the machine two things it cannot derive itself: the frame info per
  subroutine label (`frameSize`, `incomingSize`, `frameVregSlots`, and whether the
  subroutine has a frame at all), and `freeMem` as the frame area start.

---

## 5. Implementation order

1. Loader: build and expose the frame-info map and the frame area start; skip
   `FrameSlot` call arguments; keep a narrow rejection.
2. Machine: frame base/pointer state, record allocation and release, bounds checks.
3. Machine: `FrameSlot` address resolution in the memory-operand path.
4. Machine: `FrameSlot` argument delivery in `InsCALL`; frame teardown plus
   register restore in `InsRETURN`/`InsRETURNI`/`InsRETURNR`.
5. Machine: narrow (or remove) the loader rejection once 1-4 are in.
6. Tests (§7), then docs (`ideas/m68k-stack-memory-model.md`: §9, §17.4 item 4,
   §17.7 "known remaining gaps", and the status line at the top).

Modules touched: `virtualmachine` only, plus optionally `codeGenIntermediate` for
open decision §6.1. Per the project rules, `intermediate` needs no change: the IR
already carries `frameSize`, `incomingSize` and `frameVregSlots`.

---

## 6. Open decisions

### 6.1 Should `-target virtual` also *produce* frames?

`StackFrameLayout` is currently invoked only from the m68k code generator
(`codeGenM68k/.../M68kCodeGenerator.kt:33`), so VM-targeted programs never get
frames and the new VM code path would only ever be exercised by IR produced for
another target. Three options:

- **Also run the layout pass for `VMTarget`** (recommended). Recursion becomes
  testable end to end with `prog8c -target virtual -emu rec.p8`, and the existing
  VM test suite starts exercising frames, which is the strongest validation
  available. Cost: every virtual-target program's locals become frames, so
  expectations in the many `VMTarget()` compile tests (`compiler/test`, ~467 call
  sites) and the VM execution tests may need updating.
- **VM support only.** `-target virtual` output is unchanged, so nothing else
  churns, but the frame path is then only covered by tests that hand-build IR or
  load IR produced for another target, which is much weaker validation.
- **Opt-in switch** for frames on the virtual target: keeps default behavior and
  still allows the end-to-end tests, at the cost of a new user-visible option.

Decision needed before step 6 of the implementation order; the VM mechanics in §4
do not depend on it.

### 6.2 Smaller points

- Keep the 8-byte linkage area (recommended, for offset fidelity) or start the
  argument area at the record base and adjust the offsets (cheaper, but then the
  VM and the hardware would disagree about what `8(a5)` means).
- Spill only the registers in `frameVregSlots` (recommended) versus everything a
  conservative analysis says is live across calls (needs a liveness analysis that
  does not exist in the production code path, see §17.7).
- Frame overflow: dedicated budget with a clear error (recommended) versus relying
  on the 16 MB bounds check.

---

## 7. Testing

Unit level (`virtualmachine/test/TestVm.kt`, hand-built IR programs):

- A recursive subroutine with parameters and locals returns the mathematically
  correct value (guards both activation records and vreg spilling; this is the test
  that fails if `frameVregSlots` is ignored).
- Frame slot read/write: values written in one activation are not visible in
  another (the slice-2 bug, reproduced as a VM test).
- Incoming arguments: byte/word/long/pointer/float mixes land at the documented
  offsets, including the right-justified narrow slots.
- Frame teardown: after a call returns, the next activation reuses the same bytes
  (no unbounded growth for a non-recursive loop).
- Deep recursion up to a few thousand activations, then a clean overflow error.

End to end:

- With open decision 6.1 = first or third option: compile recursive and mutually
  recursive programs for `-target virtual` and compare the printed result against
  the same programs run under qemu (both executors, same expected values).
- With the second option: load an m68k-produced `.p8ir` containing frames and run
  it with `prog8c -vm`. Avoid syscalls in such a program, because m68k and VM
  syscall numbering differ; check the result through variables in memory instead.

Verification commands: `gradle :virtualmachine:test --console=plain` for the unit
tests, then `gradle build --console=plain` before finishing.

---

## 8. Risks

- **Silent wrongness if `frameVregSlots` is ignored** (§3). This is the main trap;
  the recursive unit test exists specifically to catch it.
- **Offset drift** between the VM and the hardware convention if the linkage area
  is fudged; mitigated by keeping the layout identical and by testing both
  executors on the same programs.
- **Return-value ordering**: restoring spilled registers before delivering the
  result would corrupt it (see §4.4).
- **Test churn** if the virtual target starts emitting frames (open decision 6.1);
  this is scope, not correctness.
- The loader's final "nothing unresolved" assertion (`:227`) and the artificial
  address logic do not understand frames; make sure neither starts rejecting or
  rewriting frame operands once frames are allowed.