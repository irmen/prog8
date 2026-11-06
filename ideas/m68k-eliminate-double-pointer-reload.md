# Eliminate double pointer reload in `read_le_word`-like patterns

## Problem

The wavc `read_le_word` subroutine (and similar byte-pair readers in `shared_m68k_wavfile.p8`) loads its `pointer addr` argument from the stack twice:

```asm
p8b_wavfile.p8s_read_le_word:
    link  a5,#0
    move.l  8(a5),a2
    move.b  (1,a2),d0
    and.w  #$ff,d0
    move.w  d0,d2
    lsl.w   #8,d2
    move.l  8(a5),a2        ; <-- same stack slot loaded again
    move.b  (a2),d0
    and.w  #$ff,d0
    move.w  d0,d3
    or.w    d3,d2
    move.w  d2,d0
    unlk  a5
    rts
```

## Root cause

The optimized IR contains two loads of the same parameter:

```ir
loadm.p  r14.p,[main.read_le_word.addr]
loadiz.b r15.w,[r14.p+1]
lsli.w   r15.w,#8.w
loadm.l  r17.l,[main.read_le_word.addr]    ; same 4 bytes, typed as long
loadiz.b r18.w,[r17.p]
orr.w    r15.w,r18.w
returnr.w r15.w
```

The unoptimized IR already had both loads typed as `loadm.p`; the optimizer rewrote the second one to `loadm.l`. Because the IR types differ, existing CSE / redundant-load elimination does not merge them.

`IRInstruction` validation also requires operand types to match the instruction type, so a naïve `loadr.l r17, r14` is rejected when `r14` is typed pointer.

## Scope

This plan is **only** about the double reload. Two related improvements are tracked separately and implemented first:

1. ~~Remove the dead `and.w #$ff` before a left shift by 8.~~ **Done.** The IR pattern is `loadiz.b X; lsli.w #N` (and the sign/zero-extending variants). For a left shift by `N >= resultSize - sourceSize`, the extension is dead because the shift discards all extended/sign-replicated bits. Implemented in the M68K backend (`InstrLoadStore.kt`): an extending load immediately followed by such a shift now emits just `move.<srcSize> M, dN` and lets the following shift operate on the narrow value directly. The temporary assembly peephole was removed.
2. ~~Use the shorter `moveq #0, dN; move.b M, dN` idiom instead of `move.b M, dN; and.w #$ff, dN` for zero extension.~~ **Done.** All byte/word zero-extending loads now emit `moveq #0, dN; move.<size> M, dN` in the backend.

## Proposed solution

Add an IR-level redundant-load elimination pass to `IRPeepholeOptimizer.kt`.

### Rules

1. Scan each code chunk for pairs of `LOADM`/`LOADI` instructions that read the same `MemoryReference`.
2. Allow the pair when their types are memory-compatible:
   - same type, or
   - `POINTER` ↔ `LONG`/`UWORD`/`WORD` when the target pointer size is 4 bytes, or
   - `WORD` ↔ `UWORD`.
3. Require no intervening instruction with a memory write or other side effect.
4. Replace the second load with a `LOADR` move. Reconcile the source register's type by emitting the move under the second load's type (the bits are identical).

### Alternative: backend-only fix

A narrower fix is to let the M68K assembly generator keep a "last loaded frame slot → address register" cache. This avoids IR type issues but only helps M68K and does not reduce vreg pressure for the register allocator.

The IR-level fix is preferred because it benefits every backend and removes the redundant virtual register.

## Implementation notes

- `removeDoubleLoadsAndStores()` in `IRPeepholeOptimizer.kt` is the natural place to extend; it already removes redundant `STOREM` after `LOADM`.
- The pass must be careful with I/O addresses and aliasing: only eliminate loads from frame slots or named variables, not from raw absolute addresses that may be hardware registers.
- `IRDataType` compatibility helper probably belongs in `RegisterPacker.kt` where a similar check already exists.

## Testing

1. Recompile `examples/amiga/wavc.p8` and verify `p8b_wavfile.p8s_read_le_word` loads `8(a5)` only once.
2. Recompile `readle_test.p8` and check the generated IR no longer contains two `loadm` instructions for `addr`.
3. Run the existing compiler tests and the m68k instruction-selection tests.

## Status

Not yet implemented: the double pointer reload itself still remains. The two related improvements in Scope are done and merged.
