# M68K Stack Memory Model

**Status: Vertical Slice Prototype (§17) implemented; the full model is not yet implemented.** Listed as "Deferred" in `docs/source/todo.rst`.

Design decisions taken (Oct 2026): the normal-prog8-subroutine calling
convention is a single uniform **all-stack** convention with caller cleanup
(§6.1); all-register and hybrid conventions are rejected for the baseline.
Return values stay register-based (§6.2). `asmsub`/`extsub` keep their
explicit all-register annotation-driven ABI, unchanged and separate (§6.3).

This document describes a possible M68K-target-only memory model in which
ordinary subroutine locals live in an activation record on the machine stack.
The 6502 targets retain the existing statically allocated local-variable model.

The main motivation is to make M68K subroutines reentrant and recursive without
using statically allocated storage for every local variable and parameter.

## 1. Current State

The current IR represents memory variables with fixed addresses. In particular:

- `IRStStaticVariable` describes statically allocated variables.
- `IRStMemVar` contains an absolute `address`.
- `IRSubroutine` describes parameters, returns, and code chunks, but has no
  frame-size or local-slot metadata.

Structured-IR restriction (now merged in this branch): `MemoryReference` only
knows `Direct`/`Indexed`/`Indirect`, and `AddressBase` only knows
`Symbol`/`Absolute` (`intermediate/src/prog8/intermediate/IROperands.kt`).
`Direct`/`Indexed` require `displacement >= 0`, so a frame slot such as
`-N(A5)` cannot be expressed at all. Likewise `CallSite` /
`CallLocation.ParameterMemory(name, address)` (`IRCalls.kt`) assumes a static
symbol name or absolute address, `IRSubroutine` has no `frameSize` or slot
metadata, `IRTextCodec` has no frame-slot syntax, and `VmProgramLoader`/VM
resolve every symbolic base to an absolute address before execution.

The M68K backend therefore uses static storage for normal subroutine parameters
and locals. Its current internal calling convention has the caller write
arguments into the callee's parameter variables before `jsr`.

This is not recursion-safe: a recursive call overwrites the same parameter and
local storage used by its caller.

AmigaOS library calls are a separate case. The amiga500 backend loads the
selected library base into A6 and calls an LVO through A6. A6 must therefore
remain available for library calls and cannot be the frame pointer.

## 2. Proposed Memory Model

For M68K targets only:

- Block-level variables, globals, `@shared` variables, memory slabs, and
  variables whose address must be externally visible remain static.
- Ordinary subroutine locals become stack variables.
- Ordinary subroutine parameters are passed on the machine stack by the
  caller and live in the callee's incoming-argument area (positive offsets
  from the frame pointer); see §6.1.
- Each invocation receives a distinct frame.
- Recursive and reentrant calls become valid.

The frame pointer is A5 for the amiga500 target. A6 remains available for
AmigaOS library bases. `link a5,#-N` establishes a frame and `unlk a5` removes
it. Locals are accessed with signed displacements from A5.

The frame-pointer register must be treated as reserved by the M68K register
allocator. Prog8 inline assembly on amiga500 must also document A5 as reserved
unless an explicit low-level interface says otherwise.

The qemu68k target has no AmigaOS library-base convention. It may use A6 or A5,
but using A5 for both M68K targets keeps the IR and backend behavior simpler and
avoids target-specific inline-assembly assumptions.

## 3. Frame Layout

A frame-layout pass assigns every stack-resident object an offset and alignment.
The initial implementation should allocate each object separately rather than
reusing slots based on liveness.

The layout includes:

- Local variables and local arrays (negative A5 offsets)
- Incoming parameters pushed by the caller (positive A5 offsets, padded to
  longwords; see §6.1)
- Compiler spill slots
- Any backend-required temporary slots
- Alignment padding

Note: "backend-required temporary slots" must explicitly cover the flat
`p8_regfile` virtual-register block wherever a virtual register can be live
across a call. If vreg storage stays program-static, recursion silently
corrupts live virtual registers *even before* the register allocator exists;
making that storage per-activation (frame-resident, or spilled to frame slots
around calls) is part of this design's scope, not the allocator's.

The local-area size (the `link` immediate, stored as `frameSize`) is rounded
up to an even number; the incoming-parameter area is sized separately from the
parameter list and popped by the caller after the return.

With a frame based at A5, local offsets are negative, for example:

```asm
link    a5,#-12
move.b  -1(a5),d0
move.w  -4(a5),d0
move.l  -8(a5),d0
...
unlk    a5
rts
```

The exact position of the saved A5, return address, saved registers, and local
area depends on the prologue order and must be defined by the backend. The
abstract IR should not depend on those physical details.

The compiler should enforce a per-frame limit. A first implementation could use
an 8 KiB or 16 KiB limit rather than supporting frames near the 68000's signed
16-bit displacement limit. The limit prevents individual oversized frames, but
does not prevent total stack exhaustion through deep call chains or recursion.

The compiler should report the estimated frame size and reject a subroutine
whose frame exceeds the target limit. The limit applies to the local area
(`frameSize`) in v1; the fixed per-call cost of the linkage (saved A5 + return
address) and the incoming-argument area is not capped and only shows up as
deep-chain exhaustion risk.

## 4. Compiler Auto-Temporaries on the Stack (Optional)

The compiler creates a small number of internal temporary variables during AST
desugaring. These `auto_heap_value_N` variables are generated by
`CodeDesugarer` (ongoto jump tables, ongoto index temporaries, pointer
dot-chain address temporaries) and `LiteralsToAutoVarsAndRecombineIdentifiers`
(array-literal heap hoisting). There are only four creation sites, each
producing one or two variables with short, well-defined lifetimes.

These auto-temporaries are ideal candidates for frame-slot allocation rather
than static storage. They are written once, read a few times within a single
expression or statement, then dead. They are never `@shared`, never have their
address taken in user code, and never appear in inline assembly.

The recommended approach is:

1. Keep the existing `VarDecl` nodes in the AST. They are needed for semantic
   analysis, type checking, and optimizer passes (even though the optimizer
   rarely eliminates them).
2. Mark them as compiler temporaries in the IR, either via a flag on
   `IRStStaticVariable` or by introducing a lightweight `IRFrameSlot` storage
   class.
3. The M68K codegen allocates flagged temporaries to frame slots instead of
   emitting them to `.bss` or static memory.

This is a minor optimization on its own but becomes a natural part of the
full stack frame implementation. Once subroutine locals live on the stack, these
temporaries simply become frame-resident alongside user-declared locals. The
main benefit is avoiding static storage consumption for variables that never
need static addresses.

For the 6502 targets, these auto-temporaries continue to be statically
allocated, consistent with the existing local-variable model.

## 5. Local Initialization

Prog8 variables are normally zero-initialized. The M68K implementation should
preserve this semantic rule, but need not clear every byte of every frame.

The initialization pass can clear only objects for which a read may occur before
the first write. If definite-assignment analysis proves that a variable is
written before every read, its zeroing can be omitted. Existing optimizations
that replace variables, remove unused variables, or recognize first assignments
should reduce the required initialization further.

The simplest first implementation is:

1. Use the existing optimized IR.
2. Mark frame objects that require zero initialization.
3. Emit byte, word, or long stores for those objects in the prologue.
4. Add a later optimization that coalesces adjacent zeroed ranges.

An alternative is to define stack locals as undefined on M68K, but that would
make the target's semantics differ unnecessarily from the other targets.

## 6. Calling Convention Impact

Yes, this changes the internal calling convention unless parameters remain
static. Keeping parameters static would allow a partial implementation, but it
would not make normal subroutines fully recursive or reentrant.

### 6.1 Normal Prog8 subroutines: decision, uniform all-stack

The current convention writes arguments into named parameter variables before
the call. That cannot work when those parameter variables are in the callee's
new frame, because the caller cannot write into a frame that does not yet exist.

**Decision: one uniform all-stack convention (AmigaOS C style), caller
cleanup.** No baseline exceptions, no per-signature register assignment.

Mechanics:

- The caller pushes every argument with `move.w/l` (or `fmove.x`) to `-(sp)`
  in left-to-right source order; each argument occupies a padded longword
  slot (68000 alignment rules apply: words and longs are never accessed at
  odd addresses). Narrow values are right-justified in their slot: reserve
  4 bytes (`subq.l #4,sp`), then `move.b d0,3(sp)` / `move.w d0,2(sp)` /
  `move.l d0,(sp)`. Callee-side offsets are the constant `8 + N*4` above the
  frame pointer.
- This deliberately follows the strict System V m68k scheme (uniform 4-byte
  slots) rather than the Linux/GCC m68k scheme (16-bit stack alignment,
  arguments at natural size, word-aligned). Both are legal on the 68000.
  The uniform slot is chosen because parameter offsets then never depend on
  preceding argument types, the single push rule is easy to document for
  hand-written `asmsub` callers, and it keeps v1 simple. Word-packed
  arguments are a follow-up (§6.4).
- `jsr foo`, then the **caller** pops the argument area with
  `addq.l #total_size,sp`. The callee epilogue therefore never needs to know
  the argument count; every return path is just `unlk a5` / `rts`.
- After `link a5,#-N`, the incoming arguments live at **positive** A5 offsets
  above the saved A5 and return address; the first (leftmost) argument has the
  largest offset. Locals live at negative offsets. The frame-layout pass (§3)
  assigns both areas; `frameSize` covers the locals only, the incoming size is
  derived from the parameter list.
- v1 does **not** copy incoming arguments into negative slots. The positive
  incoming slot *is* the parameter variable's storage. The exceptions are
  parameters whose *storage location* is externally constrained; those get an
  entry-time copy-in to the location the constraint demands:
  - a parameter referenced by inline assembly: copy-in to its §10 static cell
    (the caller still pushes the stack slot; the callee stores it to the
    static cell on entry);
  - a parameter referenced from a `defer` body: copy-in to its §10/§11 static
    cell, guarded by the same non-recursion check (§11, whose error rule
    counts parameters as subroutine-locals).

  Taking `&param` inside the callee is ordinary local-pointer semantics: the
  address points into the incoming area, is valid for the call's duration, and
  dangles after return under the §10 rules; no copy-in is needed for it.

Rejected alternatives:

1. **All-register** cannot be made absolute. Struct-by-value parameters are
   arbitrarily large memory objects with no register representation, so an
   "all-register" rule silently degrades into a mixed convention anyway.
2. **Hybrid (first scalar in D0, first pointer in A0, rest on stack** - the
   classic SysV m68k / Mac C ABI) was previously the recommended starting
   point and is now rejected as the baseline:
   - Its efficiency gain is downstream of a real register allocator, which the
     backend does not have yet. Today every value lives in the memory
     `p8_regfile` and D0/D1/A0/A1 are translator scratch, so passing an
     argument "in D0" merely replaces `move.l x,-(sp)` with `move.l x,d0` at
     identical cost while adding slot-clobber ordering hazards at the call
     site.
   - It requires class-dependent argument splitting agreed exactly between
     caller, callee entry, and frame layout; register-passed parameters need
     mandatory copy-in to survive the first scratch clobber, destroying the
     uniform "all parameters at positive offsets" storage model.
   - It doubles the marshalling code paths and the test matrix (scalar,
     pointer, float, struct x arity).
3. Deferred optimizations (register argument passing, word-packed slots,
   reserved outgoing-argument area) are tracked in §6.4 as explicit
   follow-ups; they are optimizations over the fixed v1 convention, never
   second conventions.

### 6.2 Returns: decision, keep register-based

**Decision: return values stay in hardware registers, unchanged by the
all-stack argument convention.** Only *arguments* move to the stack; results
do not. This is the same split every 68k ABI uses (args in memory, results in
registers), and the backend already implements it: `translateReturnValue`
(`codeGenM68k/src/prog8/codegen/m68k/InstrControl.kt:826`) reads scalar/pointer
results from `d0` and float results from the FPU accumulator, with
`CallLocation.HardwareRegister(slot)` for explicitly annotated result registers.

Rules:

- Scalar, pointer, and float results continue to use the established result
  registers. The all-stack change is orthogonal: the caller reads the result
  register immediately after `jsr`, exactly as today.
- The callee writes its result register(s) before the epilogue, and every
  return path runs the same `unlk a5` / `rts` sequence (§6.1). The result
  register is not tied to the argument area, so popping the incoming arguments
  (caller cleanup) cannot disturb it.
- Multi-value and status-flag returns: status flags are not clobbered by the
  epilogue and keep their existing branch-based handling; explicit result
  registers stay as they are.
- Pointer-in-`d0` vs in `a0` remains the existing choice (data registers by
  default, `@A0` annotation for cases that want an address register); the
  stack-model work does not change it.

This matches both m68k C ABIs (System V and Linux/GCC): integral results in
`d0`, pointer results in `a0`, float results in `fp0`; aggregate returns go
through a hidden memory buffer addressed via `a0` (SysV) or `a1` (GCC), which
is the model to follow if struct-by-value returns are ever added.

Returning a value does not require a frame change. No stack slot is reserved
for results in v1; if a future need arises (for example returning a
struct-by-value), that is a separate calling-convention addition, not part of
this design.

### 6.3 `asmsub`, `extsub`, and AmigaOS calls: decision, keep the explicit register ABI

**Decision: `asmsub`/`extsub` keep their existing explicit, all-register,
annotation-driven calling convention.** It is a separate ABI lane from the
normal-prog8-subroutine stack convention, and the frame work changes nothing
about it. This is what makes the uniform all-stack baseline affordable: the
cases where register passing genuinely matters (OS/library entry points,
performance-critical hand-written routines, IRQ-sized code) already have a
first-class way to request it via `@reg` slot annotations
(`CallingConventionSlot`, slots 10-32).

- `asmsub` continues to use explicit hardware-register annotations for both
  arguments and results.
- `extsub` continues to use its declared registers and fixed address.
- AmigaOS calls continue to load the library base into A6 and call the LVO.
- A5 must be preserved across normal calls and external calls according to the
  selected convention.

This separation prevents the normal frame implementation from changing the
existing AmigaOS ABI.

Interactions with the stack convention that must be respected:

- An `asmsub` that *calls* a normal prog8 subroutine must push the arguments
  in left-to-right longword slots and pop them after the return, exactly as
  §6.1 prescribes; or call a thin prog8 wrapper that does it. This should be
  documented for amiga500/qemu68k `asmsub` authors.
- An `asmsub` entered from prog8 code sees A5 pointing at its caller's frame.
  It must not clobber A5 (result registers D0/FP0 etc. are unaffected by the
  stack args, so reading them is unchanged). An `asmsub` entered from
  non-prog8 code has no meaningful A5; it may still call prog8 subroutines,
  whose `link`/`unlk` sequences save and restore that garbage value
  harmlessly.
- `asmsub`/`extsub` bodies keep no frame obligations of their own: establishing
  a prog8-style frame from hand-written assembly is possible (`link a5,#-N`)
  but entirely the author's responsibility.

### 6.4 Deferred optimizations (explicit follow-up split)

The v1 convention is deliberately the simple one (§6.1). These optimizations
were consciously *excluded* from v1 and must be split off as separate
follow-ups; each needs its own design review and test pass:

1. **Word-packed argument slots** (Linux/GCC m68k scheme): 16-bit stack
   alignment, arguments at their natural size (byte/bool = 2 bytes including
   pad, word = 2, long/float/pointer = 4, still even-aligned). Saves stack
   space and bus traffic per narrow argument. Impact: frame-layout pass must
   sum per-argument rounded sizes instead of using the constant `8 + N*4`
   stride; §6.3's documented asmsub push rules change. Introduce as a
   convention variant/flag, not by breaking the v1 layout.
2. **Register argument passing** as a *paired caller/callee specialization*
   derived at codegen over the fixed stack convention (model: classic SysV
   m68k / Mac C: first scalar in D0, first pointer in A0, rest on stack),
   applied only where it measurably wins (small scalar signatures). Requires
   the register allocator from `m68k-register-allocation.md` to land first;
   until then it is pure marshalling complexity with no gain (§6.1 rejection
   rationale).
3. **Reserved outgoing-argument area** inside the caller's frame: allocate the
   maximum call-site argument size once in the caller's `link` frame, write
   arguments via negative A5 offsets, drop the per-call `addq` cleanup. Most
   valuable for calls inside loops; also cleaner for the future register
   allocator (no sp churn between spills). Impact: caller frames grow by the
   outgoing area; frame-size limits (§3) must count it.
4. **Recursion-sound defers via framebase-as-parameter** (option 3 in §11):
   push `(id, framebase)` pairs on the defer handler stack, pass the parent's
   bottom-of-frame address to the generated handler as a hidden trailing
   parameter, and lower parent-local accesses in the handler to
   `Indirect(base, positive displacement)`. Lifts the §11 v1 compile-time
   rejection of recursive deferring subroutines, making defers fully
   stack-resident and recursion-sound. Also requires VM activation records to
   live in the linear memory space so saved frame bases are ordinary addresses.

Keep this list updated: any further convention-level optimization idea goes
here rather than into the v1 rules.

## 7. IR Changes

The IR should describe storage symbolically rather than naming A5 directly.
Minimal proposal for the structured IR:

- New `AddressBase.FrameSlot(val offset: Int)` variant (alternative name:
  `IRFrameSlot` storage class if symbol-table-level marking is preferred, as
  mentioned in Section 4). The offset is the frame-relative byte offset
  assigned by the frame-layout pass; the data type/size continues to come
  from the instruction's `IRDataType` and the variable declaration, plus an
  optional slot list on `IRSubroutine` for size/alignment/zero-init metadata.
- New `frameSize: Int` (default 0) on `IRSubroutine`, set by the frame-layout
  pass. A zero `frameSize` means "no frame", so all existing IR stays valid.
- Mapping: the M68K backend lowers `Direct(FrameSlot(offset), ...)` to a
  signed A5 displacement (`-N(A5)`); the VM lowers the same slot to the
  current activation record (per-call storage allocated on call, discarded
  on return). Neither backend hard-codes A5 in the IR itself.
  Prototype note (§17.5): prologue instructions (zero-clears, later
  argument copy-ins) must be *prepended into the existing first chunk* of
  the subroutine, never added as a new leading chunk - `linkChunks()`
  binds the subroutine label to `chunks.first()` and would reject or
  misbind the entry point otherwise.
- `CallLocation.ParameterMemory` needs a frame-slot form alongside its
  current static `(name, address)` form (extra optional slot/offset, or a
  separate `CallLocation.FrameSlot` variant), otherwise callers keep
  assuming statically addressable parameter variables. Under the all-stack
  convention (§6.1) the callee-side parameter slots are positive-offset frame
  slots; the caller-side push sequence and the cleanup `addq` size are derived
  from the parameter list (types/padding), so `IRSubroutine` needs no separate
  outgoing-size field beyond `parameters` and the locals `frameSize`.
- `OpcodeSchema` validation: memory-slot schemas keep accepting `Symbol`
  and `Absolute` unchanged, and additionally accept a `FrameSlot` base for
  `Direct` access. The `displacement >= 0` rule stays for symbol/absolute
  bases; only the `FrameSlot` base allows negative frame-relative offsets.
  `Indexed` over a `FrameSlot` base is allowed with a non-negative
  slot-relative displacement - this is how local arrays are accessed (array
  base slot + scaled index + non-negative displacement). `Indirect` over a
  frame base is rejected in v1; pointers into the frame are dereferenced with
  the regular `Indirect` on a pointer-vreg base, whose displacement stays
  non-negative.
- `IRTextCodec` syntax: needs a distinct round-trippable printed form for
  the new base, for example `Direct(FrameSlot(-8))` printed as `[frame-8]`.
  Symbol (`[main.var]`) and absolute (`[$1234]`) printing stays unchanged.
- `VmProgramLoader`: `pass2replaceLabelsByProgIndex` /
  `resolvedSymbolInstruction` currently fold every symbolic base into an
  absolute address and assert nothing stays unresolved. Frame-relative
  references must be excluded from that resolution and stay symbolic; the VM
  resolves them against the active frame at execution time. If VM frame
  support is deferred, the loader must explicitly reject frame-based IR
  instead of miscompiling it as static storage.

Static/absolute addressing (globals, `@shared`, slabs, all 6502 IR) is
unaffected: existing `Symbol`/`Absolute` bases, their codec syntax, and
their loader resolution keep working exactly as today.

## 8. Backend Changes

`codeGenM68k` must:

- Assign or consume frame offsets.
- Reserve A5.
- Emit the frame prologue and epilogue.
- Emit only the callee-saved register saves actually required by the allocator.
- Resolve frame slots to displacement addressing from A5, both negative
  (locals) and positive (incoming parameters).
- Marshal normal-subroutine arguments according to the uniform all-stack
  convention: caller pushes longword slots left-to-right, `jsr`, reads the
  result register, then pops the argument area with `addq` (caller cleanup).
- Emit selective local initialization.
- Reject frames beyond the target limit.
- Ensure every return path restores the frame and saved registers.

The register-allocation design must distinguish A5 from ordinary callee-saved
registers. A5 is not merely another register that a subroutine may save and
reuse; it is the base for all active frame slots.

## 9. VM Changes

To execute the same IR model, the VM needs an activation record per call. A
call must allocate the callee's frame slots, make the current frame available
for frame-relative loads and stores, and discard it on return.

This allows recursive M68K-target IR to be tested through the VM. The VM does
not need to emulate A5 or M68K instructions; it only needs to implement the
abstract frame-slot semantics.

If VM support is deferred, the compiler should reject frame-based IR when the
VM is selected rather than silently treating frame slots as static variables.

## 10. Address-Taking and Lifetime

`&local` would produce an address into the current frame. That address is valid
only until the subroutine returns.

**Decision (Oct 2026): v1 documents the dangling-pointer behavior and adds a
best-effort escape warning; it does not reject.** Dangling frame pointers join
the existing low-level pointer risk posture of the language (matching the
"accept the risk" branch); §15 step 6 implements an escape diagnostic that
warns when a frame address is stored to static memory, passed to a call, or
returned from the subroutine. A hard rejection of provable escapes can be
added later as a tightening; nothing in v1 blocks compilation on lifetimes.

Variables referenced by inline assembly, external code, `@shared`, a stored
pointer, or from `defer` statements (§11) should remain static unless the
compiler can prove that the reference does not escape. For *parameters* this
means an entry-time copy-in to the static cell (they are always pushed on the
stack by the caller, §6.1); the passing method itself never changes.

## 11. Defer, sys.exit(), and Stack Unwinding

Mechanical stack unwinding on M68K is straightforward: the epilogue restores the
caller's frame pointer and stack pointer with `unlk a5`, and `rts` pops the
return address. No unwind tables or exception-style metadata are required.

`defer` handling and `sys.exit()` unwinding are already solved at the AST level
by `DeferProcessor` (`compiler/src/prog8/compiler/astprocessing/DeferProcessor.kt`):

- Each subroutine with `defer` pushes a handler ID onto a global
  `defer_handler_stack` on entry and pops it before returning.
- Every `sys.exit()` call is augmented to invoke `defer_unwind_all`, which walks
  the handler stack and runs active defers in LIFO order before the actual
  system exit.
- `sys.poweroff_system()` and `sys.reset_system()` intentionally do **not** run
  defers and therefore need no unwinding at all.

Because defer unwinding happens through this explicit handler stack rather than
by physically walking the machine stack, introducing A5-based locals does not
change the `sys.exit()` story.

The real complication is that deferred code can reference the subroutine's own
local variables:

```prog8
sub foo() {
    ubyte x = 5
    x = 10
    defer txt.print_ub(x)     ; must see x == 10
}
```

`DeferProcessor` currently emits a separate `prog8_invoke_defers` subroutine
(inside the parent subroutine, later flattened to block scope). With static
locals that subroutine can still access `foo.x`; with stack-based locals it
would receive its own fresh frame and lose access to the parent's `-4(a5)`
slot.

Possible approaches:

1. **Inline defer code at every exit point.** The deferred statements are
   emitted directly into the parent subroutine's epilogue path, before
   `unlk a5`. This was rejected as a standalone solution: `defer_unwind_all`
   must run the defers of *outer* frames on `sys.exit()` without physically
   unwinding them, so the callable per-subroutine handler is required on that
   path regardless. Inlining would maintain two copies of every defer body
   (epilogue copy + handler copy) for no functional gain.
2. **Keep defer-referenced variables static.** Variables referenced by
   `defer` get the same "keep static" flag defined in §10 for
   address-taken / inline-asm-referenced variables; everything else moves to
   the stack. The `DeferProcessor` and the handler-stack machinery stay
   unchanged.
3. **Pass the parent frame base to the handler as a value.** The parent's
   frame base - captured right after `link a5,#-N` as the bottom-of-frame
   address `a5 - frameSize` - is pushed onto the `defer_handler_stack` next
   to the handler id, and passed to the handler subroutine as a hidden
   trailing parameter under the §6.1 stack convention. The handler reads and
   writes parent locals through the existing
   `MemoryReference.Indirect(pointer, displacement)` operand; because the
   base is the *bottom* of the frame, all parent-local displacements stay
    **positive**, and the frame base value is a plain pointer-vreg base (never
    an `AddressBase.FrameSlot`), so §7's "Indirect over a frame base is
    rejected in v1" rule is unaffected. There is only ever one frame-pointer register
   (the handler's own A5); the parent base is ordinary spillable data, so
   the earlier "needs two frame pointers" objection does not apply.

**Decision (Oct 2026): v1 takes option 2, with a compile-time rejection of the
unsound case.** The `DeferProcessor` and handler-stack machinery stay
unchanged; defer-referenced variables get the §10 keep-static flag. The only
situation where static defer-referenced variables are unsound is *two live
activations of the same deferring subroutine*, which - with a single linear
call stack - means the subroutine lies on a call-graph cycle (direct
self-recursion, or mutual recursion through a cycle that includes it). v1
rejects that at compile time rather than documenting it as a caveat:

- Error rule: a subroutine that has `defer` statements **whose bodies
  reference subroutine-local state (locals or parameters)** and that is
  reachable on a call-graph
  cycle is rejected on stack-frame targets ("defer in a recursive subroutine
  is not yet supported"), reusing the existing `CallGraph` (compilerAst).
- Non-recursive deferring subroutines are fully supported: one live activation,
  static copies are consistent, the epilogue `pop + handler call` sequence and
  `sys.exit` deep unwinding all behave correctly.
- Deferring subroutines whose defer bodies touch only globals/`@shared`,
  constants, or non-defer-referenced locals keep full stack allocation and
  recursion: their defers never read per-activation state.
- The check runs only where locals actually move to frames (m68k targets);
  6502/IR cannot recurse today, so behavior there is unchanged.
- Residual soundness limitation: the rejection follows the *static* call
  graph. Dynamic dispatch (`on subptr call`) and IRQ/task re-entry can hide a
  cycle the graph does not show. Treat every deferring subroutine that is a
  possible dynamic-dispatch target as potentially cyclic (conservative
  rejection), and document that IRQ handlers calling into deferring code of
  the interrupted program are outside the guarantee.

Option 3 is recorded as follow-up item 4 in §6.4: it is the path to
recursion-sound defers with fully stack-resident locals, removing the rejection
above entirely. It requires: the handler stack growing to `(id, framebase)`
pairs, a hidden parameter in the generated handlers, IR lowering of
parent-local references inside handlers to `Indirect(base, positive offset)`,
and the VM placing activation records in the linear memory space so the saved
base is a plain usable address.

## 12. Effects on Other Targets

Static/absolute addressing stays unchanged everywhere it exists today:
block-level variables, globals, `@shared` variables, memory slabs, and all
6502 IR keep using `AddressBase.Symbol`/`Absolute` with the current loader
resolution. Only ordinary M68K subroutine locals/parameters move to the new
`FrameSlot` base. The A5 frame-pointer / A6 library-base reservation from
Section 2 remains in force.

The 6502 targets remain unchanged:

- Locals continue to use static allocation.
- No 6502 frame-pointer or stack-slot instructions are required.
- The 6502 backend continues to use its existing parameter and register model.

The IR and frontend changes must therefore be conditional on the compilation
target or represented in a way that the 6502 code generators can continue to
lower without seeing frame storage.

## 13. Benefits

- Recursive M68K subroutines become possible.
- Subroutines become reentrant and safer for callbacks and task-like use.
- Local arrays and temporaries no longer consume permanent static storage.
- Multiple simultaneous invocations receive independent local state.
- The M68K backend gets a conventional, efficient local-access mechanism.

## 14. Risks and Open Questions

- Stack exhaustion becomes a runtime possibility.
- Prologues, epilogues, argument marshalling, and selective initialization add
  code size and execution cost.
- The internal normal-subroutine calling convention changes to the uniform
  all-stack form decided in §6.1 (complexity accepted as lower than hybrid:
  single marshalling path, no register-slot ordering hazards).
- Hand-written `asmsub` code that calls normal prog8 subroutines must follow
  the fixed push/cleanup layout; this is a documentation burden (§6.3).
- Address-taking and pointer lifetime: v1 documents dangling frame addresses
  and warns on best-effort escape detection (§10 decision).
- `defer` in recursive subroutines is **rejected with a compile error** in v1
  (§11 decision, option 2 + call-graph cycle check), so there is no silent
  unsoundness; the rejection is limited to the static-call-graph horizon
  (dynamic dispatch conservatively over-approximated, IRQ re-entry
  documented as out of guarantee). Follow-up §6.4 item 4 lifts the rejection
  via framebase-as-parameter defers.
- Inline assembly must respect A5 reservation.
- Frame layout must cooperate with register allocation and spill slots.
- Zero initialization may require additional analysis and generated code.
- The compiler needs a reliable frame-size diagnostic and hard limit.
- Existing assembly or code that assumes parameter variables have static symbols
  may need to remain explicitly static.

## 15. Suggested Implementation Order

1. Define the M68K frame-slot metadata and A5-based layout rules.
2. Keep all parameters static temporarily and implement ordinary local frames
   only for subroutines that have no parameters or calls.
3. Implement frame-safe normal-subroutine argument passing using the uniform
   all-stack convention (§6.1): caller pushes longword slots, callee reads at
   positive offsets, caller cleans up. In the same step, make call-live vreg
   storage per-activation for subroutines on a call-graph cycle (move their
   `p8_regfile` slice into the frame, per the §3 note); non-cycle subroutines
   may keep static vreg storage, since program-wide unique vreg numbering
   makes caller/callee regfile collisions impossible.
4. Add selective frame initialization.
5. Add VM activation records and recursion tests.
6. Add address-escape diagnostics and inline-assembly validation.
7. Implement the §11 v1 defer rule: mark defer-referenced variables static in
   the frame-layout pass (parameters get the §6.1 entry-time copy-in), and
   reject deferring subroutines that reference
   subroutine-local state (locals or parameters) in their defer bodies and lie
   on a call-graph cycle
   (conservatively including dynamic-dispatch targets) with a compile error.
8. Add liveness-based frame-slot reuse and frame-size reporting.

The parameter-passing change should be treated as a required part of the final
design, not as an optional optimization. Without it, stack locals improve
static-memory usage but normal recursive calls remain unsafe.

Convention-level optimizations (word-packed slots, register argument passing,
reserved outgoing-argument area) are explicitly out of scope here; they are
tracked as follow-ups in §6.4.

## 16. Sequencing against the register allocator

**Decision (Oct 2026): this stack model is implemented *before* the true
register allocator of `m68k-register-allocation.md`.** Rationale:

1. **Spill destination.** The allocator's spilling strategy (§5 of that doc)
   spills virtual registers to frame slots. Without frames there is no
   per-activation spill storage at all; an allocator built first would spill
   to the static `p8_regfile` and have that substrate rebuilt under it when
   frames land.
2. **One ABI, built against once.** This design replaces the interim
   memory-parameter convention (§6.1). The allocator's call-boundary machinery
   (§2.1, §2.5-2.7) must be written over the *final* calling convention to
   avoid guaranteed rework. The uniform all-stack convention also makes the
   normal-call clobber set signature-independent (push/cleanup through memory,
   result in `d0`/`fp0`), which collapses the open "call-site metadata
   representation" decision point in that doc's §7.1 to only `asmsub`/`extsub`
   boundaries.
3. **Feature before performance.** Frames unlock recursion/reentrancy; the
   allocator only improves code that frames already make correct.
4. **v1 has no allocator dependency.** The frame prologue/epilogue
   (`link a5,#-N` / `unlk a5` / `rts`) is self-contained while every value
   lives in memory or frame slots; no callee-saved shuffling is required yet.

Parallelizable: the allocator's convention-agnostic prep (its Stage 0/1/3:
translator restructure, virtual-register bookkeeping, slot machinery) can
proceed alongside this work. Not before frames land: spill-slot policy,
prologue/epilogue emission, and call-site marshalling.

Gated on the allocator afterwards (see §6.4): register argument passing (item
2), and the §15 step-8 liveness-based frame-slot reuse.

## 17. Vertical Slice Prototype

A deliberately minimal end-to-end implementation, built to validate this
design before committing to the full §15 sequence. It exercises every
*irreducible* format change (IR storage classes, frame layout, backend
lowering, prologue/epilogue) while deferring every interaction (arguments,
calls, defers, recursion, VM execution). If the format design is wrong, this
slice fails loudly; that is the point.

### 17.1 Scope: frameable subroutines

A subroutine is frameable when **all** of:

- the target is an m68k target (amiga500/qemu68k);
- it has **zero parameters** (avoids the whole §6.1 convention change);
- it contains **no calls** (no caller pushes, no call-live vreg relocation,
  no return-path interaction; also rules out self-recursion, since a
  self-call is a call);
- none of its locals are address-taken, referenced by inline assembly, or
  defer-referenced (they keep the §10 static path).

All other subroutines keep today's static path verbatim. Two safety
properties fall out of "no calls" for free: deferring subroutines gain a
handler-invoke call during `DeferProcessor` flattening, so they are never
frameable in the prototype (no §11 logic needed yet), and call-live vregs
cannot exist in a leaf, so `p8_regfile` may stay program-static (§3 note)
without corrupting anything.

### 17.2 Details pinned for the prototype

1. **Initialized locals become prologue stores.** `ubyte x = 5` in a sub is
   today a static variable with a baked-in init value; frame residents
   cannot work that way (the init must re-fire per call). The layout pass
   carries per-slot initial values and the prologue emits `move #v,-N(a5)`
   stores (or zero-clears). Arrays with non-zero constant init stay
   static in the prototype; zero-initialized (dirty) locals and arrays are
   cleared in the prologue.
2. **Frame residents are removed from the static variable list.** They must
   not be emitted as `IRStStaticVariable`/BSS entries at all.
3. **The VM explicitly rejects frame-based IR** (§9's sanctioned interim):
   encountering a `FrameSlot` base or a non-zero `frameSize` raises a clean
   error instead of miscompiling. VM activation records are a later slice.

### 17.3 Acceptance

- Test program: leaf sub with byte/word/dword locals, an initialized local,
  a small zero-init local array (exercises `Indexed` over `FrameSlot`).
- amiga500 asm shape checks: `link a5,#-N` at entry, `-N(a5)` accesses
  (Direct and Indexed), `unlk a5` before every `rts`, init/zero stores
  present, frame residents absent from the BSS section, vreg traffic
  unchanged.
- `.p8ir` codec round-trip test for `Direct`/`Indexed` over `FrameSlot`.
- VM rejection test.
- Frame-size limit check (§3) with a diagnostic.

### 17.4 Slice ladder (post-prototype)

1. Vertical Slice Prototype (this section).
2. Parameters + the §6.1 all-stack convention flip (the big one).
3. Call-graph cycles: call-live vreg relocation, §11 defer rejection rule.
4. VM activation records + recursion tests through the VM.
5. Diagnostics and polish: escape warnings (§10), frame-size reporting,
   liveness-based slot reuse, §4 auto-temporaries.

### 17.5 Findings

The prototype was built and verified (unit + e2e tests, and runtime
verification of a framed program on qemu-system-m68k: repeated calls to a
frameable sub correctly re-initialize and observe their own frame slots).
What it taught us:

1. **The format design slots in cleanly.** `AddressBase.FrameSlot(signed
   offset)` (offset inside the base, reference displacements staying
   >= 0) required no relaxation of any existing displacement rule. The
   sealed-interface compiler check enumerated every lowering site in one
   build pass - no hidden semantic fallout anywhere.
2. **Declaration-initialized locals needed no new mechanism.** On m68k IR
   they are already per-call `STOREIM` instructions in the first chunk;
   rewriting their memory operand to a frame slot was automatic. Only
   zero-clearing of clean (non-dirty) locals is genuinely new prologue
   code. §17.2 item 1 was simpler than expected.
3. **Hard invariant discovered: the prologue cannot be a new leading
   chunk.** `IRProgram.linkChunks()` binds the subroutine label to
   `chunks.first()` and rejects duplicate label registrations; clear
   instructions must be *prepended into* the existing first chunk. This
   must be respected by all later slices (arg copy-ins go there too).
4. **Backend cost was tiny.** `resolveMemoryBase()` is the single funnel:
   frame bases lower to one combined `displacement(a5)` string (slot
   offset + reference displacement folded together before printing - the
   existing `"$base+$disp"` concatenation string form does not work for
   frame bases). The `Indexed`/float paths reuse `lea <base>,a0` for free
   (`lea -8(a5),a0` folds through the AsmOptimizer lea-peephole
   unharmed). §7's Indexed-over-FrameSlot rule works exactly as specified.
5. **IR file format:** version bumped 4 -> 5 with the reader accepting
   4..5; `FRAMESIZE` as an optional SUB attribute (absence = 0, so all
   pre-existing IR stays valid). Only one test golden needed updating
   (the writer's version line) - a good sign for real-world blast radius.
6. **The no-calls gate is load-bearing in a good way:** it automatically
   excludes deferring subs (DeferProcessor inserts handler calls),
   self-recursive subs, and any need for call-live vreg relocation, while
   still allowing `main.start` (the backend's `bsr run_global_inits` is
   not an IR CALL, so start itself can be framed and works at runtime).
7. **Frame-layout subtleties worth keeping in §3:** slot offsets grow
   negatively from -1 (never 0); alignment must use floor-division on the
   negative cursor (naive `%` moves the cursor the wrong way); the limit
   check must fold slot + max static displacement, not just the slot; the
   prototype's fallback on limit violation is "keep the offending
   variables static and report an error" - the full design should decide
   explicitly between reject and fallback.
8. **Typed pointer locals are frameable and cheap** (4-byte address
   storage; the pointee's type is irrelevant to the slot). An early
   version of the classifier wrongly excluded them via an `isBasic` check;
   pointer-with-subtype is the common case, not an edge case.
9. **Zero-clearing is naive today:** clean arrays emit one `clr.b`-style
   store per element (8 bytes = 8 instructions). The §5 coalescing idea
   (adjacent-range stores / block clear) is confirmed as a real need
   before the full implementation.
10. **Recursion of ordinary subs is *still* broken** (verified empirically:
    a recursive fib with parameters overwrites its static param storage
    and loops). That is exactly what slices 2-3 promise to fix; the
    prototype's claim boundary ("frameable leaf subs only") held - nothing
    framed by the prototype changed the behavior of non-framed code.
11. **Float locals and static-initialized/array-initialized locals stay
    static** by deliberate prototype scope (`initializationValue != null`
    and float exclusion); the full design must add per-slot init data
    (§3/§5) instead of these exclusions.

Verdict: the design held up end to end with no architectural rework; the
format changes (§7), backend lowering list (§8), and implementation order
(§15) can be built as specified.
