# M68K prologue zero-store coalescing (follow-up 5)

**Status: planned** - design for `ideas/m68k-frames-followups.md` section 5.
Parent convention rationale stays in `ideas/m68k-frames-followups.md`; this file
only covers the prologue zero-clear emission.

## Problem

`StackFrameLayout.kt:587-588` emits one `STOREZM` IR instruction per array
element / local for every clean (non-`@dirty`, non-initialized) frame local.
The m68k backend lowers each to one `clr.b`/`clr.w`/`clr.l`
(`InstrLoadStore.kt:105-109`). A `ubyte[17]` therefore costs 17 clear
instructions (~100 bytes of code) in the subroutine prologue, every call.

## Facts established by source audit

- STOREZM integer lowering already supports BYTE/WORD/LONG on frame-slot
  targets (`clr$s <disp>(a5)`, schema allows "BWLF"): coalescing needs **no
  new opcode** for stage 1.
- a5 parity: every frame displacement is anchored to a5 after
  `link a5,#-frameSize`; SP is even everywhere in the convention (word-sized
  return address, longword arg slots, `link`/`unlk` 4-byte ops;
  `technical.rst` "Only use longword pushes"). 68000 only requires **even**
  addresses for word/longword access, not mod-4. So a coalesced run must
  start at an even a5-relative offset; head/tail bytes are `clr.b`.
  (Known pre-existing hazard: `sys.push/pop` byte builtins step SP by 1 -
  odd a5 would already break today's frame accesses, so coalescing adds no
  new invariant. Fix separately or document.)
- `@dirty` locals are skipped at line 582; static-initialized scalars take
  the STOREIM path (lines 571-580) and are never zero-cleared. Frame
  virtual-register slots (re-entrant subs) get no zero-clear by design
  (def-before-use). All three act as run boundaries.
- Float elements: `fv.elemDt` is `DataType.FLOAT` for both scalar and array;
  excluded from coalescing (stays `fmovecr`+`fmove.s` per element), matching
  the follow-ups doc.
- Different variables can be byte-adjacent (candidates sorted, cursor walks
  down with alignment rounding), e.g. two `ubyte` locals at -1/-2; but
  alignment padding (e.g. `ubyte a` + `word b`) splits runs. Merging must
  check contiguity, not just "both clean".

## Design

Replace the per-element `frameVars` prologue loop with a three-step builder:

1. **Collect zero regions**: for each `FrameVar` that is clean (not dirty,
   no Numeric initializer) and non-float, region = `[slot, slot + count *
   elemSize)`. Floats and init/dirty locals never produce regions but do
   break adjacency.
2. **Merge** regions with exact byte contiguity (`prev.end == next.start`),
   regardless of owning variable.
3. **Emit** per run `[lo, hi)` (offsets are negative, lo = lowest address):
   - one `clr.b` at `lo` if `lo` is odd, advance;
   - `clr.l` (`STOREZM LONG`) while 4+ bytes remain (start is even; every
     further longword step of 4 keeps evenness);
   - `clr.w` (`STOREZM WORD`) if 2 bytes remain;
   - final `clr.b` if 1 byte remains.
   All via existing `IRInstructions.storeZero(...)` with
   `IRMemory.frameDirect(offset)`; ordering ascending by address.

Examples (single variable, slot computed by current alignment math):

| locals                        | today      | coalesced            |
|-------------------------------|------------|----------------------|
| `ubyte[17] a`                 | 17 clr.b   | 1 clr.b + 4 clr.l    |
| `uword[8] b`                  | 8 clr.w    | 4 clr.l              |
| `ubyte a; ubyte bb`           | 2 clr.b    | 1 clr.w              |
| `ubyte a; uword w`            | 1 clr.b + 1 clr.w (pad byte -2 not touched) | same |
| `float[3]`                    | 3 x (fmovecr+fmove) | unchanged      |

### Stage 2 (optional): loop lowering detected by the m68k backend

Straight stores cost 6 bytes + ~20 cycles per longword. For long runs a
compact inline loop is better for code size: ~6 setup instructions +
`move.l d0,(a0)+; dbra d1` regardless of run length. Per-byte time is
slightly worse than straight `clr.l`; the win is code size and uniform
prologue length for huge arrays.

Mechanism: no IR change at all. Stage 1 already emits each zero run as
consecutive `STOREZM` instructions with ascending even a5 offsets, so the
decision is pure instruction selection and lives in `codeGenM68k`: when
`translateChunk` lowers a `STOREZM LONG` on a `frameDirect` operand, look
ahead for K consecutive `STOREZM LONG` instructions whose displacements step
by +4 with nothing interleaved; if K >= threshold (start: 16 longwords = 64
bytes, tunable constant), emit the inline loop for the whole run and consume
those instructions; otherwise lower each instruction as today. Head/tail
`clr.b`/`clr.w` are never part of a longword run and stay straight.
Scratch d0/d1/a0 are free between IR instructions (virtual registers live
in the regfile or frame slots), framed subroutines cannot contain inline
assembly by the frameability rules, `invalidateD0Cache()` must run around
the loop, and the loop label comes from `AsmGen.makeLabel`
(`AsmGen.kt:252`). Detection also works for hand-edited `.p8ir` input to
the standalone m68k backend, and is semantically identical either way.

Rejected alternative (explicit `ERASEFRAME` opcode + IR format bump): the
information the backend needs is already fully carried by the STOREZM
stream, so a new opcode would add format/version surface and rejection
logic in other backends for no gain. The cost of run detection is one
implicit contract: `StackFrameLayout` must keep emitting each zero run as a
contiguous, address-ascending block of STOREZM instructions with nothing
interleaved - document the invariant in both files.

### Stage 3 (rejected): `jsr sys.memclear`

`sys.memclear(long @D0, uword numbytes @D1)` exists in
`shared_m68k_memory_routines.p8` and is a fast `movem.l d0-d6/a1` bulk
clearer. Rejected as prologue mechanism:

- **SP is not actually the problem**: frame slots are a5-relative after
  `link`, and `jsr`/`rts` is net-zero SP churn; argument pushes go to
  a5-relative incoming slots. The frame stays valid across the call.
- The real blockers:
  - `IRUnusedCodeRemover` runs *before* `StackFrameLayout`
    (`M68kCodeGenerator.kt:27-33`), so a `sys.memclear` that the user's
    source never references is not in the IR program - the synthesized call
    would dangle. Fixing that means force-keeping stdlib code because of a
    compiler-internal pass: cross-phase coupling.
  - `sys.memclear` clobbers d0-d7 and a1-a3. Harmless only because a
    prologue has no live physical registers; that invariant silently couples
    the pass to placement and to future hardware-slot register use.
  - Fixed call overhead (plus hardware-slot argument marshalling in IR) only
    pays off for sizes the compiler already warns about per call
    (`StackFrameLayout.kt:472`).
  - New IR-level constructs (target-stdlib call injection) expand the IR
    surface every backend and the VM must reject.
- Stage 2's inline loop gets the same bounded-code-size property with none
  of the coupling. If benchmarking later shows huge per-call clears
  dominate, revisit: cleanest variant is then an m68k `asmsub` *injected
  alongside the IR program* by the same pass, not a call into stdlib.

## Implementation steps

1. `StackFrameLayout.kt`: extract the prologue zero-emission (lines
   570-590) into `emitPrologue(frameVars, program.st)`; implement region
   collect/merge/aligned-emit as above. Keep STOREIM/copy-in/float paths
   exactly as today. Keep the large-array warning (line 472).
2. Unit tests (see below), run `:compiler:test --tests
   "prog8tests.compiler.TestStackFrameLocals"` and `:codeGenM68k:test`
   (module names per gradle config), then `gradle installdist
   installshadowdist` and compile the example to inspect the listing.
3. Stage 2 only after stage 1 lands: run detection + threshold constant in
   `codeGenM68k`; no IR format changes; second test pass.

## Tests

New in `compiler/test/TestStackFrameLocals.kt` (compile `qemu68k`,
`writeAssembly=true, assemble=false`, assert on parsed lines of
`subAssembly()`, counts not raw dumps):

1. "byte[17] prologue clears with five stores" - exactly 4 `clr.l` + 1
   `clr.b`, zero other zero-clears in the sub's prologue run.
2. "uword[8] prologue clears with four longword stores" - 4 `clr.l`, no
   `clr.w`.
3. "adjacent byte locals coalesce across variables" - `ubyte a; ubyte b` ->
   1 `clr.w`, 0 `clr.b`.
4. "alignment padding splits runs" - `ubyte a; uword w` -> 1 `clr.b` and 1
   `clr.w`, no `clr.l`.
5. "@dirty locals are not cleared" - `ubyte @dirty d; ubyte c` -> exactly 1
   `clr.b` (for c).
6. "initialized locals keep their init store" - `ubyte x = 42` -> a
   `move.b #$2a,-1(a5)` style store present, no `clr` for its slot.
7. "float arrays keep per-element FPU clears" - `float[3]` -> 3 `fmove.s`
   to `(a5)` slots, no `clr.l` covering float bytes.
8. "dirty neighbour splits a run" - `ubyte[4] c; ubyte[4] @dirty d` ->
   exactly 1 `clr.l` (for c), none for d.

Regressions to check (expected unaffected, verify by running):
`TestStackFrameEmission.kt` (hand-built IR, bypasses StackFrameLayout),
`TestM68k.kt` (counts `link`/`unlk` only), no m68k file-size goldens exist
(`TestCompilerOnExamples` has no m68k specs).

Stage 2 additions: `TestStackFrameEmission.kt` tests feeding
threshold+1 vs threshold-1 consecutive `STOREZM LONG frameDirect`
instructions (displacements stepping +4), asserting the loop shape (setup,
label, `move.l d0,(a0)+`, `dbra`) appears in the first case and only
straight `clr.l` in the second; plus a `TestStackFrameLocals` end-to-end
test that a `ubyte[128]` prologue contains a `dbra` loop and at most 3
straight clears.

## Example program

`examples/qemu68k/zero-coalescing.p8` (short; verifies semantics *and*
shows the optimization in the listing):

```prog8
%import textio

main {
    uword failures = 0

    sub check_frame() {
        ubyte[17] small        ; coalesces to 4 x clr.l + 1 clr.b
        uword[8] words         ; adjacent -> 4 x clr.l
        float[2] floats        ; excluded, per-element FPU clears
        ubyte init42 = 42      ; init store, run boundary
        ubyte[4] @dirty scratch ; never cleared, run boundary
        ubyte[64] big          ; stage-2 loop candidate

        for uword i in 0 to 16 {
            if small[i] != 0 { txt.print("FAIL small\n"); failures += 1 }
        }
        for uword i in 0 to 7 {
            if words[i] != 0 { txt.print("FAIL words\n"); failures += 1 }
        }
        for uword i in 0 to 63 {
            if big[i] != 0 { txt.print("FAIL big\n"); failures += 1 }
        }
        if floats[0] != 0.0 { txt.print("FAIL float\n"); failures += 1 }
        if init42 != 42 { txt.print("FAIL init\n"); failures += 1 }
    }

    sub scribble_stack() {
        ubyte[128] junk        ; frames at the same sp region check_frame will use
        for uword i in 0 to 127 { junk[i] = $AA }
    }

    sub start() {
        txt.print("m68k frame zeroing test\n")
        check_frame()
        scribble_stack()
        check_frame()          ; must still see zeroes: prologue re-clears every entry
        if failures == 0 { txt.print("ALL OK\n") } else { txt.print("FAILURES\n") }
    }
}
```

Verification:

- `prog8c -target qemu68k examples/qemu68k/zero-coalescing.p8` then inspect
  `check_frame` in the `.asm`: count the `clr` instructions before/after
  (stage 1: `small` 17->5, `words` 8->4, `big` 64->16; stage 2: `big`
  becomes a short `dbra` loop).
- Run under qemu68k (as `qemubootinfo.p8` is run) to confirm "ALL OK": the
  `scribble_stack` + second call proves fresh activations observe zeroes
  even when the underlying stack bytes held garbage.

## Risks / out of scope

- Behavior-visible only for framed m68k subroutines; static path, VM and
  6502 backends untouched.
- Do not fold the `sys.push/pop` SP-parity hazard into this change; file it
  as a separate follow-up note in `m68k-frames-followups.md`.
- Threshold tuning (stage 2) needs real timing on 68000; keep stage 1
  threshold-free (it strictly reduces instruction count).
