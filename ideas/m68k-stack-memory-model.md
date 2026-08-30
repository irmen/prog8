# M68K Stack Memory Model

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
- Ordinary subroutine parameters are copied into the callee's frame on entry,
  or are assigned frame slots by the calling convention.
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

- Local variables and local arrays
- Frame-resident parameters
- Compiler spill slots
- Any backend-required temporary slots
- Alignment padding

The size is rounded up to an even number. Word and long accesses must never be
placed at odd addresses on the 68000.

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
whose frame exceeds the target limit.

## 4. Local Initialization

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

## 5. Calling Convention Impact

Yes, this changes the internal calling convention unless parameters remain
static. Keeping parameters static would allow a partial implementation, but it
would not make normal subroutines fully recursive or reentrant.

### 5.1 Normal Prog8 subroutines

The current convention writes arguments into named parameter variables before
the call. That cannot work when those parameter variables are in the callee's
new frame, because the caller cannot write into a frame that does not yet exist.

A frame-safe convention must be selected. Possible choices are:

1. Pass parameters in registers and have the callee copy them into its frame.
   This is efficient for small fixed signatures and fits the existing M68K
   register-slot machinery.
2. Pass parameters on the machine stack. The callee accesses incoming values at
   positive A5 offsets after its prologue.
3. Use a hybrid convention: pass the first values in registers and spill
   excess values to the caller's stack area.

The recommended initial design is a hybrid convention, with explicit IR
argument metadata. It avoids the current shared parameter variables while
remaining efficient for common small subroutines. The callee's entry sequence
copies incoming register arguments into frame slots when later code needs a
stable address or when the value must survive a call.

The exact choice must also account for byte, word, long, pointer, and float
values, including alignment and multi-value returns.

### 5.2 Returns

The existing return convention can remain initially: ordinary scalar returns
use the established M68K result registers, while explicit `asmsub` register
annotations continue to control assembly-subroutine results. Returning a value
does not require a frame change, provided the epilogue runs before `rts`.

All return paths must branch through a common epilogue or otherwise emit the
same A5 restoration and callee-saved-register restoration.

### 5.3 `asmsub`, `extsub`, and AmigaOS calls

The external calling convention should remain separate from normal Prog8
subroutines:

- `asmsub` continues to use explicit hardware-register annotations.
- `extsub` continues to use its declared registers and fixed address.
- AmigaOS calls continue to load the library base into A6 and call the LVO.
- A5 must be preserved across normal calls and external calls according to the
  selected convention.

This separation prevents the normal frame implementation from changing the
existing AmigaOS ABI.

## 6. IR Changes

The IR should describe storage symbolically rather than naming A5 directly.
Possible additions include:

- A stack-variable or frame-slot storage class
- A frame offset and data type for each frame object
- `frameSize` on `IRSubroutine`
- A list or mask describing saved callee registers
- Metadata describing incoming parameter locations
- A list of frame objects requiring zero initialization

The serialized IR should identify the target and storage class clearly. Existing
6502 IR must continue to use absolute/static storage.

The IR should not encode `-4(a5)`. Instead, it should encode a frame-relative
slot. The M68K backend maps that slot to A5, while the VM maps it to the current
activation record.

Whether prologue and epilogue operations are explicit IR instructions or are
generated from `IRSubroutine` metadata is an implementation choice. Backend-
generated prologues are preferable initially because saved-register selection,
instruction forms, and frame-pointer choice are backend details.

## 7. Backend Changes

`codeGenM68k` must:

- Assign or consume frame offsets.
- Reserve A5.
- Emit the frame prologue and epilogue.
- Emit only the callee-saved register saves actually required by the allocator.
- Resolve frame slots to displacement addressing from A5.
- Marshal normal-subroutine arguments according to the new convention.
- Emit selective local initialization.
- Reject frames beyond the target limit.
- Ensure every return path restores the frame and saved registers.

The register-allocation design must distinguish A5 from ordinary callee-saved
registers. A5 is not merely another register that a subroutine may save and
reuse; it is the base for all active frame slots.

## 8. VM Changes

To execute the same IR model, the VM needs an activation record per call. A
call must allocate the callee's frame slots, make the current frame available
for frame-relative loads and stores, and discard it on return.

This allows recursive M68K-target IR to be tested through the VM. The VM does
not need to emulate A5 or M68K instructions; it only needs to implement the
abstract frame-slot semantics.

If VM support is deferred, the compiler should reject frame-based IR when the
VM is selected rather than silently treating frame slots as static variables.

## 9. Address-Taking and Lifetime

`&local` would produce an address into the current frame. That address is valid
only until the subroutine returns. The compiler must either:

- Track and reject addresses that escape the frame lifetime, or
- Document dangling-pointer behavior and accept the existing low-level risks.

Variables referenced by inline assembly, external code, `@shared`, or a stored
pointer should remain static unless the compiler can prove that the reference
does not escape.

## 10. Effects on Other Targets

The 6502 targets remain unchanged:

- Locals continue to use static allocation.
- No 6502 frame-pointer or stack-slot instructions are required.
- The 6502 backend continues to use its existing parameter and register model.

The IR and frontend changes must therefore be conditional on the compilation
target or represented in a way that the 6502 code generators can continue to
lower without seeing frame storage.

## 11. Benefits

- Recursive M68K subroutines become possible.
- Subroutines become reentrant and safer for callbacks and task-like use.
- Local arrays and temporaries no longer consume permanent static storage.
- Multiple simultaneous invocations receive independent local state.
- The M68K backend gets a conventional, efficient local-access mechanism.

## 12. Risks and Open Questions

- Stack exhaustion becomes a runtime possibility.
- Prologues, epilogues, argument marshalling, and selective initialization add
  code size and execution cost.
- The internal normal-subroutine calling convention must change.
- Address-taking and pointer lifetime rules need to be defined.
- Inline assembly must respect A5 reservation.
- Frame layout must cooperate with register allocation and spill slots.
- Zero initialization may require additional analysis and generated code.
- The compiler needs a reliable frame-size diagnostic and hard limit.
- Existing assembly or code that assumes parameter variables have static symbols
  may need to remain explicitly static.

## 13. Suggested Implementation Order

1. Define the M68K frame-slot metadata and A5-based layout rules.
2. Keep all parameters static temporarily and implement ordinary local frames
   only for subroutines that have no parameters or calls.
3. Implement frame-safe normal-subroutine argument passing.
4. Add selective frame initialization.
5. Add VM activation records and recursion tests.
6. Add address-escape diagnostics and inline-assembly validation.
7. Add liveness-based frame-slot reuse and frame-size reporting.

The parameter-passing change should be treated as a required part of the final
design, not as an optional optimization. Without it, stack locals improve
static-memory usage but normal recursive calls remain unsafe.
