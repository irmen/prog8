# Pointer Dereference Codegen Analysis

## Bug

In the old 6502 codegen, a bare typed-pointer dereference such as:

```prog8
^^pointer temp = &table
while temp^^ != $0000 {
    temp++
}
```

corrupted `temp` itself. The generated code loaded the value pointed to by `temp` and wrote it back into `temp`, so the next iteration read from the wrong address.

## Root Cause

`PointerAssignmentsGen.deref()` in `codeGenCpu6502` does not return a uniform abstraction. Depending on the length of the pointer-deref chain, it returns one of three different things:

| Chain length | Example | What `deref()` returns |
|--------------|---------|------------------------|
| 0 | `temp^^` | the pointer variable itself |
| 1 | `foo.bar^^` | address of the final pointer field |
| >1 | `foo.bar.baz^^` | address of the final value (the pointer stored in the final field is loaded into `P8ZP_SCRATCH_PTR`, offset 0) |

`assignPointerDerefExpression()` then had to know which case it received. The original code always performed an extra word load and wrote it back into the returned pointer variable:

```kotlin
asmgen.loadIndirectWordAY(zpPtrVar, offset1)
asmgen.out("  sta  $zpPtrVar |  sty  $zpPtrVar+1")
```

This is safe when `zpPtrVar` is the temporary scratch pointer `P8ZP_SCRATCH_PTR` (field-access cases), but wrong when `zpPtrVar` is the user's own pointer variable (bare `temp^^`), because it overwrites the variable.

## Applied Fix

Changed the extra dereference in `assignPointerDerefExpression()` to run only for non-empty chains:

```kotlin
if(value.derefLast && value.chain.isNotEmpty()) {
    asmgen.loadIndirectWordAY(zpPtrVar, offset1)
    asmgen.out("  sta  $zpPtrVar |  sty  $zpPtrVar+1")
    offset = 0.toUByte()
} else {
    offset = offset1
}
```

- Bare variable derefs (`temp^^`) now read directly from the variable without corrupting it.
- Struct-field pointer derefs (`foo.bar^^`, `foo.bar.baz^^`) still get the extra dereference they need, because `deref()` uses `P8ZP_SCRATCH_PTR` for those cases.

Tests added:

- `double pointer dereference in while loop (code gen fix)` — covers the bare-variable case.
- `pointer-to-pointer struct field deref still double-derefs on 6502` — preserves the existing double-deref behavior for struct fields.

## Comparison with IR-Based Codegens

The IR codegens (`codeGenIntermediate`, used by virtual, `new6502`, and m68k) handle this more cleanly. In `ExpressionGen.kt`:

1. `translate(startpointer)` evaluates the base pointer into a register.
2. `traverseRestOfDerefChainToCalculateFinalAddress()` walks the chain and always produces a register that points at the final value (or a field offset when there is no final dereference).
3. A single `LOADI` reads from `(pointerReg, offset)`.

The empty-chain case falls out naturally: the traverse function does nothing, so the load reads directly from the startpointer register. The caller never has to decode different abstraction levels based on chain length.

## Remaining Technical Debt

The precise inconsistency is narrower than the chain-length table suggests. For chains of length 1 and >1, `deref()` already returns the same abstraction: a pointer to the final value plus a Y offset. Only the empty-chain case differs: `deref()` may return the user's own zeropage pointer variable (when `isZpVar()`), and by construction any caller write-back into the returned variable is forbidden there, whereas in all other cases the returned variable is `P8ZP_SCRATCH_PTR`, which callers may safely write back into.

The current fix only prevents the write-back in `assignPointerDerefExpression()`. A proper cleanup would make `deref()` always return `P8ZP_SCRATCH_PTR` (copying the user's pointer there when the chain is empty), or alternatively make the contract explicit with a "may clobber the returned pointer" flag that callers must obey. Either form restores one uniform abstraction for all chain lengths and lets `assignPointerDerefExpression()` treat every case identically, matching the IR codegen structure in `ExpressionGen.kt`. The trade-off of always copying to the scratch pointer is one extra 2-byte copy for the common bare-variable case.

## Verified Semantic Fact (empirical)

Test program on 2026-10-19 (both targets compiled with the current compiler build):

```prog8
struct Inner { ^^uword p }
struct Outer { ^^Inner inner }
^Outer o; ^Inner ip
o.inner = ip ; ip.p = $1000 ; pokew($1000, $aaaa) ; pokew($aaaa, $1234)
result = o.inner.p^^
```

- `virtual` target: prints `aaaa`.
- `c64` target: generated assembly computes `AY = word at (scratch)` (= value of field `p`, `$1000`) followed by `jsr prog8_lib.func_peekw`, so `result = *(p content)` = `aaaa` as well.

So both backends agree on the invariant: **for `derefLast` chains the value is `*(pointer stored in the final field)`; each pointer in the chain is followed exactly once, plus one read of the pointed-to word.** This validates the fix's premise (double deref for `foo.bar^^`, via implicit follow of `o` plus follow of the stored pointer). Note also that `CodeDesugarer` rewrites many scalar-field deref chains into `peekw/peekl` calls before codegen (see `CodeDesugarer.kt:1569-1622`), so `deref()` only ever receives the shapes that survive desugaring; the exact inventory of surviving shapes is part of phase 0 below.

## Proposed Plan: normalize `deref()` to a single contract

### Target contract (mirrors the IR codegen)

`deref()` always returns `(zpVar, offset)` such that **one indirect access through `zpVar` at byte `offset` reaches the final thing**:

- `derefLast=true`: `(zpVar),offset` is the address of the value itself. One word read yields the value; one memory write with `(zpVar),offset` stores it.
- `derefLast=false`: `(zpVar),offset` is the location of the field content (typically a pointer field); reads and writes go directly through it.
- `addOffsetToPointer=true` and `loadPointerFieldValue=true`: unchanged; this mode is already "materialize the effective address" and every current subbranch already consumes all follows it needs (`PointerAssignmentsGen.kt:279-304`, follows for `derefLast`).

Key point: this is precisely the IR codegen's contract (`traverseRestOfDerefChainToCalculateFinalAddress` returns a reg+offset pair) so that the same single `LOADI`-equivalent works everywhere.

### Current conformance gaps (all in `deref()`, only for `derefLast`)

| shape | follows inside `deref()` | conforms |
|-------|--------------------------|----------|
| chain 0 (`temp^^`)      | 0 (`deref()` returns the variable itself) | yes (`(temp),off` already reaches the value) |
| chain 1, `addOffsetToPointer=true` | final field followed (`deref:282-284`) | yes |
| chain 1, `addOffsetToPointer=false` | final field NOT followed (`deref:286-304`); caller must follow | **no** |
| chain >1                | every field followed, including the final one (`deref:312-328`) | yes |
| chain 1/`>1`, `derefLast=false` | field content location returned with offset | yes |

So chain>1 vs chain<=1 asymmetry is the *only* structural leak: the fixed `assignPointerDerefExpression()` has to decide "was the final follow consumed?" by testing `chain.isNotEmpty()`.

### Phase 1 - move the final follow into deref() (mechanical)

In the `chain.size==1 && !addOffsetToPointer && derefLast` subbranches (`PointerAssignmentsGen.kt:286-304`, both the `isZpVar` and the copy-to-scratch path), call `updateScratchPointer()` right after `addFieldOffsetToScratchPointer()` and return `(P8ZP_SCRATCH_PTR, 0)`. After phase 2 removes the now-duplicated caller-side follow, the follow is neither duplicated nor dropped.

Phase 1 is NOT size-neutral or behavior-neutral: only `assignPointerDerefExpression()` has a matching follow to delete. Every other caller of `deref()` with default args receives a followed pointer plus one extra `updateScratchPointer` (~8 bytes) for chain==1 `derefLast`, with nothing to remove on their side. The affected call sites are the assign* helpers (`PointerAssignmentsGen.kt:37-230`, writes and inplace negate/invert), `inplaceModificationPointerField` (`AugmentableAssignmentAsmGen.kt:134`), the const-index pointer-array read (`AssignmentAsmGen.kt:262`), the address-of read paths (`AssignmentAsmGen.kt:563,749`, `PointerAssignmentsGen.kt:945`), and the `AugmentableAssignmentAsmGen` RMW dispatch. For write targets (`foo.bar^^ = v`) this also changes semantics: today those helpers store into the field location (`&foo.bar`), after phase 1 they store through the followed pointer (`*(foo.bar)`), which matches the IR backend. This is an intended behavior change, not a relocation; size deltas of a few bytes UP per affected site are expected and must be attributed per the documented rule.

### Phase 2 - delete the caller's private final follow (mechanical, small)

Only one call site has a compensating follow to delete: `assignPointerDerefExpression()` (`PointerAssignmentsGen.kt:397-408`) is the only place in the codebase with the `loadIndirectWordAY(zpPtrVar, offset1)` + `sta $zpPtrVar | sty $zpPtrVar+1` write-back pattern (verified by grep on 2026-10-19). Delete the entire `if(value.derefLast && value.chain.isNotEmpty())` block there; the bare-read path that was the "fix" stays as the only path. Do not hunt for further follows to remove: every other `loadIndirectWordAY(zpPtrVar, 0u)` site is either behind `addOffsetToPointer=true` (a branch phase 1 does not touch) or is an RMW load of the current value being multiplied, divided, or reduced (`PointerAssignmentsGen.kt:1941-2104`), which must be kept.

Also required: a comment update to `derefUsesScratchPointer()` to match the new contract; no behavioral change there (which paths use `P8ZP_SCRATCH_PTR` does not change - we only added one follow instruction inside already-scratch paths).

### Phase 0 (before phase 1) - instrument the surviving shapes

`CodeDesugarer` already converts a large class of scalar-field deref chains into `peek/peekw/peekl` calls, so `deref()` is only exercised for a subset of shapes. Build a dev-only instrumented `deref()` that logs `(chain.size, derefLast, addOffsetToPointer, loadPointerFieldValue, isZpVar)` tuples across `prog8c` on the whole `examples/` tree and the compiler's own test corpus, plus all new tests. Any tuple that does not match the conformance table above is discovered before it can break a caller-cleanup edit.

### Phase 3 - verification

- Extend `TestPointers.kt` with tests for the branch phase 1 actually changes - chain==1 `derefLast`: a read (`foo.bar^^`, asserting the double deref result, as a `simulate()`-based test) and a write (`foo.bar^^ = v`, asserting the store lands at `*(foo.bar)`, not in the field). The existing test `pointer-to-pointer struct field deref still double-derefs on 6502` is the regression anchor for the read side and must stay green.
- Note what does NOT cover the change: the discriminating `$aaaa`/`$1234` chain>1 read program from the section above is desugared into `peekw(...)` before the 6502 codegen (detriment #1), so a `simulate()` test of it exercises the desugarer+peekw path and passes regardless of phase 1/2 correctness. A chain>1 write (`x.inner.p^^ = v`) exercises the chain>1 branch, which phase 1 does not modify. Both are fine as additional coverage but cannot substitute for the chain==1 tests.
- `prog8tests.compiler.TestPointers` green; then `TestCompilerOnExamples` expected sizes, following the documented rules: diff the generated `.asm` of shifted examples before updating each expected size (phase 1 shifts sizes UP slightly at chain==1-`derefLast` sites - see phase 1; phase 2 shifts sizes down slightly).
- Final guards: `-noopt` baseline runs are already covered by existing tests; no new failure modes expected because no optimizer participates in this change.

### Alternative rejected: explicit three-way contract

A cheaper fix would change the return type to a small data class `(zpVar, offset, finalFollowConsumed: Boolean)` so each caller branches on data instead of guessing by chain length. Rejected as the main plan because it makes the leak permanent: every current and future caller must still branch, and the internal-only inconsistency survives. It remains the right fallback if phase-2 caller cleanup turns out to be riskier than expected.

### Potential detriments: cases the implementer must verify

The normalization itself touches only the chain==1-`derefLast`, `addOffsetToPointer=false` subbranches of `deref()` (chain 0, chain>1, and all `addOffsetToPointer=true` paths are untouched), but the following cases can be silently damaged by an over-eager phase-2 cleanup. Each lists the today-evidence and what to verify before and after the edit (compile with and without `-noopt`, diff `.asm`):

1. **Chain>1 value reads have already been desugared away - do not delete their "follows".**
   Verified on 2026-10-19: `result = o.inner.p^^` reaches the codegen as `peekw(<derefLast=false chain arg>)` (desugarer rewrite); the emitted asm shows exactly one `updateScratchPointer` (the chain>1 `derefLast` branch is bypassed) plus `jsr prog8_lib.func_peekw`. The `loadIndirectWordAY(zpPtrVar, 0u)`-shaped site that loads the field pointer for the peek argument is a **field read**, not a leftover follow. Phase 2's "delete the self-follow" rule applies **only** to the `loadIndirectWordAY(...)` + `sta $zpPtrVar | sty $zpPtrVar+1` write-back pattern behind `derefLast=true`. Deleting a plain derefLast=false value load corrupts the peek argument.

2. **Address-of-derefLast shapes change meaning under phase 1.**
   `PtAddressOf` on a chain (`AssignmentAsmGen.kt:740-764`, `PointerAssignmentsGen.readByteByAddressOfDereference:943-953`): for chain 0, today's behavior (copy the pointer variable's content = the value address) is correct and unchanged. For chain 1 with `derefLast`, today's callers consume the returned var as "location holding the pointer to be dereferenced"; after phase 1 `deref()` returns the followed pointer instead, shifting such reads/writes by one indirection level. Verify whether any `&(chain-1-derefLast)` shape survives `CodeDesugarer` at all; if yes, align to the IR backend's semantics (address-of-value = the followed pointer) and add `simulate()` regression tests for it. (The swap paths in `AsmGen.kt` are NOT affected: the non-optimized swap calls `deref(v, true)` with `addOffsetToPointer=true`, and the zeropage fast path at `AsmGen.kt:1550` bypasses `deref()` entirely.)

3. **RMW / augmented-assignment paths must keep using the same scratch layout.**
   `AugmentableAssignmentAsmGen.inplaceModificationPointerField` has a hard `require(zpPtrVar=="P8ZP_SCRATCH_PTR")` and relies on `derefUsesScratchPointer()` to decide between in-place-through-variable vs the preserve-address-on-stack scheme. Phase 1 must not flip any of those decisions (chain-0 and chain-1-`!derefLast` zp fast paths stay zp-var-based). Verify with before/after asm diffs on `temp^^ += 1`, `foo.bar^^ += 1`, and bare derefLast targets; any flip from user-var to scratch (or back) changes the clobber-preservation assumptions and needs paired edits.

4. **`derefUsesScratchPointer()` re-derivation.**
   No behavior change is expected (it keys on which paths use the scratch pointer, and phase 1 keeps exactly those paths scratch-bound), but grep callers for other places that branch on "deref returned a user variable for chain==1" (`isZpVar` checks inside callers) and update comments/conditions where the contract terminology changed.

5. **Sizes.**
   Phase 1 shifts sizes UP slightly (one extra `updateScratchPointer` per chain==1-`derefLast` default-arg call site; see phase 1), phase 2 shifts sizes down slightly (the single deleted follow in `assignPointerDerefExpression()`). `TestCompilerOnExamples` expected sizes may also shift if the desugarer/CPU-optimizer interacts with restructured patterns. Follow the documented rule: revert + re-diff the generated `.asm` for each shifted example before bumping an expected size.

### Explicitly out of scope: existing frontend pointer-chain issues

Several pointer-chain compilation problems exist independently of `deref()` and belong to the deferred `todo.rst` item ("really fixing the pointer dereferencing issues - cursed hybrid between IdentifierReference, PtrDereference and PtrIndexedDereference"). Verified on 2026-10-19 on the current build: `while o.inner.p^^ != $aaaa { ... }` crashes the compiler with `FatalAstException: binexpr with '.' operator should have been converted into PtrDereference or something else` (AstChecker.kt:2603) - distinct frontend bug, ditto for `-noopt`. The `deref()` normalization work must NOT attempt to fix these.

Practical rules for the implementer:

- Phase 0's shape inventory must be recorded *per usage context* (assignment RHS, if/while condition, call argument, augmented assignment, swap, address-of), because `CodeDesugarer` produces contextually different shapes and some contexts don't compile at all today.
- Frontend crashes found during phase 0 are recorded as known-broken input values for the inventory, and are not fixed here.
- Regression policy: if a program fails after the refactor, first reproduce on the pre-refactor build (revert, rebuild). Only failures that appear with the refactor but not without it are in scope for this change; everything else is a separate bug report against the deferred pointer-hybrid item.
- The alternative "always return `P8ZP_SCRATCH_PTR` including chain 0" variant listed under Remaining Technical Debt IS detrimental (extra 2-byte copy + follow on every bare-deref read, the hottest shape - e.g. the while-loop scan that motivated the original fix); the follow-relocation plan above deliberately avoids that cost. If that variant is ever revisited, re-measure the bare-deref loops first.

See `docs/source/todo.rst` for the tracking item.
