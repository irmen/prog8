# M68k Codegen Quality Improvements

Plan to bring the emitted M68k assembly closer to vbcc/gcc-m68k output quality.
Ordered from **lowest complexity / highest impact** (top) to **highest complexity**
(bottom). Each item lists the affected component, the expected benefit, and rough
effort. Status: proposal.

## Current state

- Backend is an IR-op-by-op lowering (`codeGenM68k`), not an optimizing code
  generator. Output carries full-line IR debug comments (`; load.b r925.b,#1.b`,
  `; call p8b_txt.p8s_print(...)`) plus `; source:` lines (~35% of the emitted
  .asm in textelite), and uses address-mode choices driven by the IR rather
  than by the CPU.
- Register allocator is intraprocedural, chunk-level liveness with greedy
  coloring over D2-D6 / A2-A4 / FP2-FP7. Good, but leaves a tail of spills.
  Uncolored vregs spill primarily to A5 frame slots; the global `p8_regfile`
  is nearly unused in real output (textelite and maze: 474 / 208 frame-slot
  accesses vs 3 regfile mentions each - one variable + label).
- `AsmOptimizer` peephole already exists and is the cheapest lever. Note: each
  pass re-implements its own `controlTransfers` set; hoist a shared constant
  before adding more passes.
- Hand-written library routines (`sys.memclear`, etc.) are the quality ceiling;
  compiler-generated code sits well below.

## Status notes from the 2026-11 review (verified against code + real output)

- `optimizeConditionalOverJump` already implements conditional-branch-over-bra
  inversion (3.4 is partially done).
- `optimizeByteZeroExtendSafe` already implements the `moveq #0` zero-extend
  variant with full flag-safety scanning (3.2's peephole half is done).
- `optimizeStoreReloadToRegCopy` already handles all move sizes (2.6's
  "byte/word sizes" is done; only frame slots remain).
- The 16384 frame limit and `%option noframe` protections already exist
  (`M68kCodeGenerator.kt`); 4.1's work is only the promotion policy.
- `TestCompilerOnExamples` has no m68k expected-size table (classes cover
  C64 / Cx16 / Both / Virtual / PET32 only).

## Tier 4 re-measurement (2026-11-04, 30 programs across qemu68k + amiga1200)

Compiled all examples for both qemu68k and amiga1200 targets and counted A5
frame-slot accesses (`(off,a5)`) vs `p8_regfile` references in the generated
`.asm`. Results:

| Program (target) | Frame slots | Regfile refs | Ratio |
|---|---|---|---|
| textelite (qemu68k) | 474 | 3 (2 accesses + label) | ~158:1 |
| maze (qemu68k) | 206 | 3 (2 accesses + label) | ~69:1 |
| qemubootinfo (qemu68k) | 112 | 3 (2 accesses + label) | ~37:1 |
| 3d (amiga1200) | 343 | 75 (74 init-staging + 1 label) | ~4.6:1 |
| adpcmbench (amiga1200) | 200 | 73 (72 init-staging + 1 label) | ~2.7:1 |
| custominputs (amiga1200) | 289 | 75 (74 init-staging + 1 label) | ~3.9:1 |
| viewdemo (amiga1200) | 258 | 75 (74 init-staging + 1 label) | ~3.4:1 |
| blitcop (amiga1200) | 250 | 75 (74 init-staging + 1 label) | ~3.3:1 |
| gfxdemo (amiga1200) | 238 | 73 (72 init-staging + 1 label) | ~3.3:1 |
| wavc (amiga1200) | 229 | 73 (72 init-staging + 1 label) | ~3.1:1 |
| tictactoe (amiga1200) | 167 | 73 (72 init-staging + 1 label) | ~2.3:1 |
| window3d (amiga1200) | 149 | 73 (72 init-staging + 1 label) | ~2.0:1 |
| stream-adpcm (amiga1200) | 134 | 73 (72 init-staging + 1 label) | ~1.8:1 |
| circles (amiga1200) | 126 | 73 (72 init-staging + 1 label) | ~1.7:1 |
| bdmusic (amiga1200) | 88 | 113 (112 init-staging + 1 label) | ~0.8:1 |
| hlaudio (amiga1200) | 69 | 113 (112 init-staging + 1 label) | ~0.6:1 |
| (remaining amiga programs) | 4-85 | 73-77 (all init-staging + 1 label) | ~0.05-1.2:1 |

**Key finding: the register allocator's spill path to `p8_regfile` is essentially
never exercised.** Every regfile reference that does exist falls into one of
three categories, none of which are register-spill decisions:

1. **Struct instance initialization staging** (`move.l #addr,p8_regfile+N` then
   `move.l p8_regfile+N,var`) — the compiler uses regfile slots as a temporary
   holding area for struct instance addresses during startup init. Present in
   all amiga1200 programs (72-112 references each).

2. **System startup library code** (`move.l #libname,p8_regfile+0` / `clr.l
   p8_regfile+4` / `movea.l p8_regfile,a1` / `move.l p8_regfile+4,d0` /
   `jsr exec.OpenLibrary`) — hand-written or compiler-generated startup code
   that opens AmigaOS libraries. Present in all amiga1200 programs (5
   regfile read/write pairs each, in `init_system`).

3. **`sys.die.code` staging** (`move.w sys.die.code,p8_regfile+0` then
   `move.w p8_regfile+0,d0`) — a single store-reload pair. Present in
   qemu68k programs only (2 accesses + label).

**Zero spill-pattern accesses** (`move.X dN,p8_regfile+N` or
`move.X p8_regfile+N,dN` for computation) were found in any program. The 5
"spill-pattern" grep hits in amiga programs are all from category 2 (system
startup), not from register pressure.

**Conclusion for Tier 4:** The premise that "the regfile is the default fallback
for uncolored vregs" is not borne out by real output. The allocator already
sends virtually all spills to A5 frame slots. The regfile's remaining uses are
initialization and library code, not register-spill traffic. This
significantly reduces the expected benefit of 4.1-4.5:

- **4.1 (widen promotion)**: Low benefit — there is almost nothing to promote
  because the allocator already sends spills to frame slots. The regfile
  references that exist are not spill decisions.
- **4.2 (prefer frames over regfile)**: Low benefit — the regfile is already a
  last resort in practice; it is not being used as a default spill target.
- **4.3 (improve coloring heuristic)**: Still potentially valuable — better
  coloring could reduce frame-slot traffic (the dominant spill target: 4-474
  accesses per program). This is the one Tier 4 item that could yield
  measurable gains.
- **4.4 (interprocedural hints)**: Unchanged — could reduce caller-saved kill
  sets, but high effort and deferred.
- **4.5 (eliminate regfile entirely)**: Not feasible without refactoring — the
  regfile is still needed for struct-instance init staging and system startup
  code. These code paths would need to be rewritten to use frame slots or
  direct moves before the BSS label can be removed. The assembler would catch
  any missed reference, but the refactor scope is larger than originally
  estimated.

## Tier 1 - Cosmetic / trivial (hours each) [ALL DONE]

### 1.1 Strip IR debug comments from emitted asm [DONE]
- **Where**: `AsmGen.kt` emission + `Instr*.kt` lowering (full-line IR
  comments such as `; load.b r925.b,#1.b`, `; call ...`, `; storeim.b ...`,
  plus `; source:` lines).
- **Benefit**: cleaner listings, smaller `.asm`, no functional change.
- **Effort**: small.
- **Done**: single choke point (`translateInstruction`) gated on new
  `-ircomments` CLI flag, plumbed through `CompilationOptions.irComments`
  (builder, `CompilerArguments`, daemon protocol + roundtrip test). Default
  off: textelite `.asm` went 8013 -> 6180 lines with identical assembled
  output. `; source:` lines stay unconditional. Docs entry added in
  `docs/source/compiling.rst`.
- **Note**: one existing test (`TestM68k` status-flag case) locates LOADHR via
  its echo comment and now compiles that fixture with `irComments = true`.

### 1.2 Normalize whitespace / mnemonics [DONE]
- **Where**: `AsmGen.kt`.
- **Benefit**: consistency with vasm idioms; easier diffing.
- **Effort**: small.
- **Done**: `emitLine` normalizes every generated line to lowercase mnemonic +
  exactly two spaces before operands (operands untouched: string literals stay
  case-sensitive, `d1:d0` pairs intact). All `SECTION`/`ALIGN` literals
  lowercased to `section`/`align`. Verified on textelite/maze: zero
  uppercase-led code lines, zero 3-space gaps, both still assemble.
- **Note**: hand-written `%asm` in library sources (`syslib.p8`,
  `shared_m68k_math.p8`) passes through verbatim and keeps its own style
  (e.g. padded `lea     x,a0`, uppercase `ALIGN`); deliberately not touched.

## Tier 2 - Peephole expansion (days)

### 2.1 Redundant move elimination [DONE]
- `move.l dK,dK`, `move.l aK,aK` no-ops; `move.l d0,d0` after a computation.
- **Where**: `AsmOptimizer.kt`.
- **Effort**: small.
- **Note**: near-zero expected win: `optimizeSwapBack` already removes the
  common duplicate-move pair and the literal same-register form did not occur
  once in the textelite or maze samples. Keep but never schedule it ahead of 2.3.

### 2.2 Flag-aware tst removal [DONE]
- If a preceding `add`/`sub`/`lsl`/etc. that sets N/Z already covers the
  following `tst`.
- **Where**: `AsmOptimizer.kt` (extend `optimizeRedundantTst`).
- **Effort**: small-medium (need per-mnemonic flag tables).
- **Note**: reuse the existing `nFlagOverwriters` / `nFlagOpaque` tables
  (already encode a per-mnemonic flag model in `zeroExtendFlagDecision`)
  instead of building a second parallel table.
- **Done, rescoped by flag analysis**: `tst` clears V/C, so removing it after
  `add`/`sub`/`cmp` (which set V/C/X) is only sound with a liveness proof -
  deliberately NOT built (and zero such cases occur in real output anyway).
  Built instead: data-register destinations (the old pass only handled
  memory, missing all 15 real cases) plus first-op coverage for the
  flag-identical set (`moveq`, `and`/`or`/`eor` all forms, `clr`, `not`,
  `ext`/`extb`, `swap`, `tst` itself) - all clear V/C and preserve X exactly
  like `tst`, so no branch analysis is needed. `movea`/`lea` (no flags) and
  the V/C setters stay excluded.
- **Measured** (pass disabled vs enabled): 20 redundant `tst` removed, all
  `move`-to-`Dn` cases - textelite -16B (6), gfxdemo -28B (8), tictactoe -12B
  (4), maze unchanged. First real binary shrinkage of the campaign
  (-56 bytes total; deltas exceed the 2B/instr from branch-shortening
  cascades). Remaining `tst`s verified load-bearing (after labels/branches,
  `movea`, `movem`).

### 2.3 Immediate form selection [DONE]
- `move.l #N,dK` -> `moveq #N,dK` when -128..127 (extend the existing
  `optimizeMoveqWordForm` to long immediates and to `add`/`sub`/`cmp`/`and`/
  `or`/`eor`).
- `addi/subi #N` -> `addq/subq` for 1..8 (real gap: no peephole for this;
  real output contains 118 `addi/subi` in textelite even though the lowering
  already emits `addq/subq` directly for increments and some immediates, see
  `InstrArithmetic.kt`).
- **Where**: `AsmOptimizer.kt`.
- **Effort**: medium.
- **Note**: the `.l` moveq case is much easier than the existing `.w` one
  (full register write, sign-extends identically to `move.l #N`, so no
  flag/liveness forward scan needed).
- **Done**: new `optimizeMoveqLongForm` (unconditional: full write, identical
  flags; normalizes unsigned `$FFFFFF80..$FFFFFFFF` to signed) and
  `optimizeAddSubQuick` (matches the plain `add.`/`sub.` forms the lowering
  actually emits, 1..8 plus add<->sub flip for -8..-1; skips ccr/sr).
  Verified on textelite/maze: zero narrowable immediates left, both assemble.
- **Note**: `cmp`/`and`/`or`/`eor` have no quick forms on m68k (only
  `addq`/`subq`/`moveq` exist), so that part of the item was dropped after
  investigation. (The old "118 addi/subi" count turned out to be IR echo
  comment lines, not instructions; the real targets were the plain
  `add.`/`sub.` forms.)

### 2.4 Address-arithmetic combining [DONE]
- `move.l a0,d0; add.l #off,d0` -> `lea (off,a0),a1` where legal; combine
  consecutive `add`/`sub` on the same register.
- **Where**: `AsmOptimizer.kt`.
- **Effort**: medium.
- **Done, rescoped by measurement**: only 3 foldable pairs exist in all of
  textelite/maze (all in shared lib code), and data-register merging was
  dropped (flag-unsafe: `(x+a)+b` vs `x+(a+b)` can differ in V; zero
  consecutive data-reg pairs occur anyway). Built `optimizeAddressArithmetic`:
  `movea.l`/`move.l X,aY` + `.w`/`.l` add/sub/addq/subq/adda/suba `#N` to the
  same `aY` folds to `lea`. Soundness: An-destination ops never touch flags;
  `movea` sources fold freely; `move.l` sources need the existing
  flag-scan (stricter: bare labels stop it); `$hex` sources are skipped (may
  be MMIO reads); data-reg destinations never fold. The doc's `d0`->`a1`
  example as stated is unsound (renames the destination) and was not built.
- **Measured**: 3 sites/program (`lea (1,a0),a2`, `lea (4,a3),a0`,
  `lea ptr+2,a2`); binaries byte-identical (link alignment absorbs it).
  Encoding check showed the brief-form folds are size-neutral (4B->4B) but
  save ~8 cycles each on the `adda`/`move.l`-abs cases; only the symbol case
  saves bytes (2B).

### 2.5 Redundant lea reload (extend) [DONE]
- Existing `optimizeRedundantLea` only tracks `a0`. Extend to `a1`-`a4` and
  across straight-line code; invalidate on calls/branches as today.
- **Effort**: medium.
- **Note**: semantics differ per register: a0 is scratch (current per-call
  invalidation is correct); extend to a1 keeps scratch semantics, while
  a2-a4 are callee-saved so invalidation across `jsr` relaxes. Treat a1 and
  a2-a4 as two separate, differently-scoped passes.
- **Done, with negative findings**: measured real output first - 100% of
  compiler-emitted `lea` target `a0` (61 in textelite, 56 in maze); the only
  `lea` to `a1` anywhere are two singleton hand-written library loads
  (`math.crc16/crc32`), never redundant pairs. So the a1-a4 half was
  deliberately NOT built (untestable dead code). What was built: the pass
  moved inside the fixpoint loop so branch removals can join straight runs
  and vice versa, with a regression test proving the cascade
  (lea removal unblocking conditional-over-jump). Measured delta on
  textelite/maze/amiga-gfxdemo: zero (identical .asm modulo build headers,
  identical binaries). Binding constraint is calls killing scratch `a0`
  between accesses (see maze `cells` case) - that needs callee analysis,
  i.e. item 4.4, not this pass.

### 2.6 Store-reload to reg copy (extend) [DONE]
- Existing pass handles `move.S d0,(mem)` then `move.S (mem),d1` ->
  `move.S d0,d1` for all sizes already (byte/word extension is done).
- Extend to frame slots (`(off,a5)`), currently rejected by
  `isSafeMemoryOperand()` because it rejects any parenthesised operand.
- **Effort**: medium.
- **Note**: keep excluding `(aN)+` / `-(aN)` post/decrement operands
  (dropping the read would break the pointer's advance).
- **Done**: new `parseFrameSlot` helper accepts all emitted forms (`8(a5)`,
  `-2(a5)`, `(-8,a5)`, `(a5)`) and backs both the widened store-reload guard
  and `parseLeaBase` (whose old `frameSlotPattern` only matched `(-d,a5)`,
  missing every real positive/unsigned slot). Store-reload parsing switched
  from naive comma-split to paren-aware `splitOperands` (identical for plain
  operands; previously frame operands misparsed and silently never matched).
  `(aN)+`/`-(aN)`, `$hex` and mismatched offsets/sizes stay excluded.
- **Measured** (frame half disabled vs enabled): 5 conversions, all in maze
  (`move.w d4,-8(a5)` / `move.w -8(a5),d0` -> `move.w d4,d0`, etc.),
  maze -12B; textelite/gfxdemo/tictactoe unchanged (zero consecutive
  frame store-reload pairs there - stores and reloads never sit adjacent).

### 2.7 Fold load-const-then-operate [DONE]
- `move.#N,dX` / `op dX,dY` -> `op.#N,dY` where the op supports immediates
  (spotted: `move.b #8,d3` / `or.b d3,d2` -> `ori.b #8,d2` in inlined maze
  bodies). Same value, identical flags (both set N/Z from the result, clear
  V/C); the move is dead if `dX` is otherwise unused between the two lines.
- **Where**: `AsmOptimizer.kt` (new pass) or lowering.
- **Note**: needs a use-scan for `dX` between the pair (or restrict to pairs
  where `dX` is a scratch/allocated temp); without it the move must stay.
- **Effort**: small-medium.
- **Done**: new `optimizeConstOperateFold` for `add`/`sub`/`and`/`or`/`eor`/
  `cmp`/`move` (second-line sizes, `moveq` first lines included). `btst`-style
  ops excluded (they preserve incoming flags); `addi`-family to `An`
  excluded (register form legal, immediate form not); `sr`/`ccr` excluded.
  dX-dead scan reuses the file's label/control-transfer conventions, safe only
  on a covering overwrite (`move.l`/`moveq`/same-size `move`/`clr`).
- **Measured** (pass disabled vs enabled): maze -12B, gfxdemo -4B,
  tictactoe -4B, textelite 0 (conversions fire but alignment absorbs them -
  e.g. `move.l #sym,d2` / `move.l d2,-4(a5)` -> `move.l #sym,-4(a5)`).
  Conversions individually verified sound by inspection.

## Tier 3 - Instruction selection at lowering (days-weeks)

### 3.1 Use 68020 scaled indexing directly [DONE PARTLY]
- **Where**: `InstrLoadStore.kt` / `resolveMemory()`.
- On 68020, emit `move.l (base,didx.l*scale),d0` instead of manual mul + add.
- Gate on `cpu.is68020`.
- **Effort**: medium.
- **Done, mostly verify-first**: all four `Indexed` branches (int/float x
  allocated/staged) already emitted scaled forms with correct 68020 gating -
  proven by compiling an int+float indexing probe for both CPUs (020 uses
  `(a0,d3.w*2)`, `(0,a0,d2.l*4)`; 000 uses `add.w`/`lsl.l` + unscaled forms).
  The single real gap: the float *staged* branch manually scaled even on 020
  (`lsl.l` + `(0,a0,d0.l)`). It now folds to `(0,a0,d0.l*scale)` for 2/4/8.
  Sound: D0 holds the word index zero-extended (`moveq #0` + WORD load), so
  full-`.l` scaling is identical. Encoding verified with vasm (020+68881
  accepts, 68000+68881 rejects - the gate is load-bearing, and the earlier
  scare was just a missing `-m68881` flag in the manual check).
- **Measured**: zero corpus occurrences of the staged-float shape (no
  `(0,a0,d0.l)` anywhere in textelite/maze), so binaries byte-identical;
  coverage is fixture tests (020 scaled + 000 manual) instead.
- **2026-11-06 scope correction - pointer-indexed access is NOT covered.** The
  scaled-indexing path only triggers on IR `MemoryReference.Indexed` reads
  (named arrays). Pointer-indexed arrays (`values[pos]` where `values` is a
  `^^uword`/`^^long`) go through `ExpressionGen.translatePointerIndexing`
  (`codeGenIntermediate/ExpressionGen.kt`), which folds the element scale
  into the index **in-register at IR level** (`loadmz` + `lsl.l` +
  `addr.l` + `LOADI Indirect[reg]`), because `MemoryReference.Indexed`
  requires a static `AddressBase` and cannot base on a register-held pointer.
  By the time the backend sees it there is no index/scale left to fold into
  `(aN,dN.w*2)`, so 68020 targets produce the same manual scale dance as the
  68000. This affects every pointer-indexed loop on all m68k targets (the
  shared sorting lib, list/graph traversal, gfx loops). See 5.3 for the fix.

### 3.2 Cheaper zero-extension [DONE]
- `move.b x,d0; and.l #$ff,d0` -> `moveq #0,d0; move.b x,d0` (or `move.b` +
  `extb.l` on 68020).
- **Effort**: small.
- **Note**: the peephole half already exists (`optimizeByteZeroExtendSafe`,
  with full flag-safety scanning). What remains is emitting `extb.l` at
  lowering on `cpu.is68020`.
- **Done, with correction**: `extb.l` sign-extends, so it is NOT valid for the
  zero-extension case above (that half stays `moveq`+`move.b`). What it does
  replace is the *sign*-extension pair `ext.w`+`ext.l` (byte->long). The
  `emitSignExtendByteToLong` helper already existed and was used on arithmetic
  paths; the two remaining manual pair sites (`LOADMSL`, `LOADIS`/`LOADISL`)
  now route through it. Verified live: `move.b`+`extb.l` on qemu68k,
  `ext.w`+`ext.l` preserved on amiga500, both assemble.

### 3.3 Better addressing modes [DONE, mostly verify-first]
- Use post-increment `(a0)+` / pre-decrement `-(a0)` where the IR expresses
  sequential access; use displacement with base where offsets are small.
- **Where**: `resolveMemory()` + `InstrLoadStore.kt`.
- **Effort**: medium.
- **Done, rescoped by measurement**: displacement is already complete
  (`(off,An)` indirect, `sym+off` direct) and post-increment exists everywhere
  the IR expresses sequential access (45 uses in textelite, 34 in maze -
  memcopy/memset inline paths). The remaining shapes need other tiers, not
  addressing selection: load-bump-writeback triples (`move.l 8(a5),a0` /
  `move.b d2,(a0)+` / `move.l a0,8(a5)`, 18 in textelite) need liveness for
  the writeback plus regalloc to keep the pointer in `An` (Tier 4/5.2);
  `lea base,a0` + `(a0,dN)` pairs (42/33) cannot fuse displacement for any
  saving (`lea` is absolute-long either way; dropping it needs CSE/liveness,
  Tier 5). Zero foldable zero-displacement indexed forms occur. Leftovers
  re-homed, nothing buildable left in this item's stated scope.

### 3.4 Tail-call and branch optimization [DONE]
- Conditional-branch-over-`bra` inversion already exists
  (`optimizeConditionalOverJump`); `bsr+rts` -> `bra` already exists
  (`optimizeTailCall`). Remaining work: `jmp` PC-relative where in range.
- **Effort**: medium.
- **Done**: audit showed `JUMP`/`CALL` to labels already emit `bra`/`bsr`
  (vasm sizes them, falling back if out of range); the only absolute jump to
  a local label left was `emitBranch`'s taken path when leaving a subroutine.
  It now emits `bra` for label targets (new `targetIsAbsolute` parameter
  threaded through all branch helpers; absolute/indirect/library-dispatch
  forms keep `jmp`/`jsr`, which cannot be PC-relative). Verified on
  textelite: the sole remaining `jmp label` is a hand-written library
  tail-call, not generated code.

### 3.5 Multiply-by-constant strength reduction [DONE]
- **Done**: implemented in `codeGenM68k` lowering (`InstrArithmetic.kt`) for
  immediate `mulu`/`muls` (byte/word/long) when the destination is a data
  register. The backend decomposes `x * N` into a shift-add-sub recipe and
  falls back to the native multiply when no profitable recipe exists.
- The recipes work for both signed and unsigned multiplication by a positive
  constant: left-shift and add/sub are linear over two's complement, so the
  same chain produces the correct low bits for `muls` as well as `mulu`.
  Signed negative constants are now handled too: the positive recipe is
  computed and the final result is negated. For unsigned `mulu`, a negative
  immediate is rejected and the original multiply is kept.
- Examples: few-bit constants (`10x = (x<<3)+(x<<1)`), subtraction forms
  (`29x = (x<<5)-(x<<2)-x`, `31x = (x<<5)-x`), factored forms (`45x` via
  `9x` then `5x`). Classic minimal-chain recipes (Bernstein); a small table
  of common strides (array widths, screen rows, struct sizes) covers nearly
  all real occurrences.
- **Where**: `InstrArithmetic.kt` (`emitMulOp`), with the recipe generator in
  `MultiplyByConstant.kt`.
- **Gate by CPU and term count**: aggressive table on 68000 (`mulu.w` is ~70
  cycles, nearly every 2-3 term recipe wins; allow up to ~3-4 terms);
  short recipes only on 68020 (`muls.l` is ~28-30 cycles, chains run ~8-16;
  limit to ~2 terms). Off on 68030/68040/68060 because the hardware
  multiplier is fast enough that shift-add chains are unlikely to help and
  can be slower. Prog8 currently has no 030/040/060 targets, so the practical
  cutoff is 68000 vs 68020, but the implementation checks `compTarget.cpu`
  and disables the optimization for anything newer than 68020.
- **Benefit**: speed first, size second. Real occurrences exist: `mulu.w #29`
  in `cellidx` and float-index paths. Baseline: vbcc `-O2` also emits
  `mulu.w #29` here, so this is a spot to beat vbcc, not just match it -
  measure against the `available_uncarved` comparison setup.
- **Measured effect on `examples/amiga` (amiga500 / 68000)**:
  - `blitcop`: 2 `muls.w #100` -> 0
  - `circles`: 1 `mulu.w #40` -> 0
  - `gfxdemo`: 1 `mulu.w #24` -> 0
  - `screen`: 1 `mulu.w #3` -> 0
  - `tictactoe`: 7 `mulu.w`/`muls.w` -> 0
  - `viewdemo`: 2 `mulu.w` -> 0
  - `iff`: 3 -> 1 remaining `mulu.w #3` (non-data-register destination path)
  - Binary size increased slightly where the optimization fired (e.g.
    `tictactoe` +88 B, `viewdemo` +28 B, `blitcop` +28 B) because the
    shift-add chains are larger than the 4-byte `mulu.w` instruction.
- **Measured effect on `examples/qemu68k/maze.p8`**:
  - `amiga500` (68000): 26 `mulu.w` -> 0, binary +420 B (8712 -> 9132)
  - `qemu68k` (68020): 31 -> 25 `mulu.w`, binary +72 B (19503 -> 19575);
    the 25 remaining are all `mulu.w #29` (3-term recipe, above the 68020
    limit of 2 terms)
- **Effort**: medium.
- **Limitations**: only the data-register destination path is optimized. If
  the compiler has to stage the multiply through `d0` (spilled destination,
  memory source, etc.) the original `mulu`/`muls` is kept.
- **Loop-gating rejected**: the easy signal (`loopNestingDepth > 0` in `AsmGen`)
  only covers counted `IRLoopChunk` (dbra) loops, and real hot loops never
  arrive as those - maze's `.p8ir` contains zero `IRLoopChunk`s (`repeat`,
  `while`, even `for` all lower to plain chunks with back-edge branches).
  Covering those needs a CFG back-edge walk (medium work of its own), so the
  policy stays static cost-per-CPU instead.

### 3.6 IR-level multiplication by power of two [DONE]
- **Where**: `simpleAst/src/prog8/code/optimize/ExpressionOptimizers.kt`
  (`optimizeAlgebraicIdentities`). `x * 2^n` is rewritten to `x << n` for
  integer types (signed and unsigned, since a left shift fills with zeros and
  is exact for powers of two). `x * 0` -> `0` and `x * 1` -> `x` are also
  handled, with the side-effect guard on the zero case. Negative powers of
  two (`x * -2^n`) become `-(x << n)`.
- **Benefit**: all targets (IR-level, so 6502, M68K, virtual), not just M68K.
- **Effort**: small.
- **Test coverage**: `compiler/test/TestOptimization2.kt` "multiplication by
  negative power of two (simpleAst)" already exercises the rewrite; positive
  powers and the `0`/`1` identities are covered by the same code path in
  `optimizeAlgebraicIdentities`.

### 3.7 Constant call arguments load straight into the hardware register slot [DONE]
- **Where**: IR `CallArgument` (`intermediate/IRCalls.kt`, `IRTextCodec.kt`),
  argument selection in `codeGenIntermediate/ExpressionGen.kt`, lowering in
  `codeGenM68k/InstrControl.kt` and `codeGenNew6502/InstrControl.kt`.
- A constant integer, float, or address-of-global argument passed to a call in
  a CPU register slot is now a call-argument immediate (`#42.w@s0`,
  `#main.items@s18`) instead of a virtual register that had to be materialized
  and then moved into the slot. The vreg disappears entirely, so the backend
  emits the constant into the slot register directly (`moveq #42,d0`,
  `movea.l #main.items,a2`).
- **Measured** (`examples/amiga/wavc.p8`, amiga500): `.text` 16428 -> 16268
  bytes; `movea.l #...,a2` 84 -> 3; `movea.l a2,a0` 85 -> 1.
- **Limits**: an immediate argument needs an explicit hardware-register call
  location; stack and frame-slot arguments still take a register. Locals and
  parameters are never folded to symbol immediates because their frame address
  is not known at that point.
- **Other targets**: new6502 passes immediates directly for slots s0-s2
  (A/X/Y); the virtual target is unaffected (no hardware-register slots).

## Tier 4 - Register allocation & spilling (weeks)

> **RE-MEASURED 2026-11-04 (30 programs, qemu68k + amiga1200):** The premise
> is confirmed but the framing needs adjustment. Regfile traffic from actual
> register spilling is **zero** in all programs. The regfile references that do
> exist are from struct-instance init staging, system startup (library code),
> and `sys.die.code` staging — none are spill decisions. A5 frame slots are
> the dominant spill target (4-474 accesses/program). The "largest quality gap"
> framing is partially outdated: 4.1/4.2 have low expected benefit (the
> allocator already does what they propose), but 4.3 (coloring heuristic) could
> still reduce frame-slot traffic. 4.5 (eliminate regfile) requires refactoring
> init/startup code paths that currently use regfile as staging. See the
> re-measurement table above for full data.

### 4.1 Widen StackFrameLayout promotion
- **Where**: `StackFrameLayout` (M9) in `M68kCodeGenerator.kt`.
- Promote more vregs (not only long-lived) to A5 frame slots instead of the
  global `p8_regfile`.
- **Benefit**: removes `p8_regfile` traffic; frame-relative loads are cheaper
  and reentrant-safe.
- **Effort**: medium.
- Constraint already enforced: the 16384 frame size limit and `%option noframe`
  checks exist in `M68kCodeGenerator.kt`; this item is only the promotion
  policy.
- **Re-measurement note (2026-11-04)**: The allocator already sends virtually
  all spills to A5 frame slots. Regfile traffic from register spilling is
  zero in all 30 measured programs. The regfile references that do exist are
  from struct-init staging and system startup, not spill decisions. Expected
  benefit is **low** — there is almost nothing to promote. Consider
  downgrading priority or combining with 4.3 (coloring heuristic) which
  targets the actual dominant spill target (frame slots).

### 4.2 Prefer frames over regfile as the spill target
- Make the regfile a last resort (globals / non-reentrant state) rather than
  the default fallback for uncolored vregs.
- **Effort**: medium, depends on 4.1.
- **Does NOT remove the regfile**: this only reduces traffic. Some cases still
  legitimately need the regfile fallback (unallocated vregs, raw `%asm`
  textual references, float spills). `%option noframe` subs are NO LONGER in
  this list — they will use static in-memory variables instead (see 4.5).
  Actual removal of the `p8_regfile` / `p8_fregfile` BSS labels is 4.5 and
  must remain conditional.
- **Re-measurement note (2026-11-04)**: The regfile is already a last resort
  in practice — the allocator does not use it as a default spill target. Zero
  spill-pattern accesses were found in any of the 30 measured programs.
  Expected benefit is **low**. The regfile's remaining uses (struct-init
  staging, system startup) are not spill decisions and would not be affected
  by this change.

### 4.3 Improve coloring heuristic
- Current greedy by liveness. Add cost model: prefer keeping pointer-typed
  vregs in address regs (A2-A4), data in D2-D6; spill cheapest (fewest uses,
  longest live range) first.
- **Effort**: medium.
- **Re-measurement note (2026-11-04)**: This is the most promising Tier 4
  item. Frame-slot accesses are the dominant spill target (4-474/program).
  Better coloring could reduce these by keeping more vregs in hardware
  registers. The current greedy heuristic has no cost model, so it may spill
  hot vregs prematurely. This is the one Tier 4 item likely to yield
  measurable code-size and performance gains.

### 4.4 Cross-chunk / limited interprocedural hints
- Inline small callees' register usage to avoid full caller-saved kill sets at
  call boundaries (when callee is known and small).
- **Effort**: high. Defer.

### 4.5 Eliminate p8_regfile / p8_fregfile entirely
- End state after 4.1/4.2 should be near-zero regfile traffic (today already 3
  mentions each in textelite/maze: one variable plus the label itself). This
  item removes the section, not just the traffic: stop emitting the
  `p8_regfile` / `p8_fregfile` BSS labels (`AsmGen.kt` `emitBssVars` tail)
  when nothing references them.
- **Where**: `AsmGen.kt` (emission + `resolveRegFileAddress` fallback),
  `M68kCodeGenerator.kt` (spill-slot path), `VregRenumber.kt` (bails when the
  regfile layout is observed).
- **Measure first**: enumerate remaining references after 4.1/4.2 by cause -
  unallocated vregs (empty allocator map), raw `%asm` textual refs
  (`inlineAsmMayReadRegisters` can only see the regfile symbols textually),
  float spills. `%option noframe` subs are removed from this list — they will
  use static variables, not the regfile.
- **Re-measurement note (2026-11-04)**: Even after 4.1/4.2, the regfile
  cannot be fully eliminated. The remaining references are not from register
  spilling but from:
  - **Struct instance initialization staging** (72-112 references/program on
    amiga1200): `move.l #addr,p8_regfile+N` then `move.l p8_regfile+N,var`.
    These would need to be rewritten to use direct moves or frame slots.
  - **System startup library code** (5 read/write pairs/program on amiga1200):
    `move.l #libname,p8_regfile+0` / `clr.l p8_regfile+4` / `movea.l
    p8_regfile,a1` / `move.l p8_regfile+4,d0` / `jsr exec.OpenLibrary`.
    These are in hand-written or compiler-generated startup code.
  - **`sys.die.code` staging** (2 accesses/program on qemu68k): a single
    store-reload pair.
  - **`%option noframe` subroutines**: These bypass the register allocator
    entirely (`allocateSubroutine` skips them, setting `spilledVregsBySub =
    emptyList()`). In codegen, `operand()` falls through to `regAddr()`,
    which returns `p8_regfile+N` for any unallocated vreg (all of them, since
    noframe subs get no allocation). So noframe subs currently depend on
    `p8_regfile` for **all** their temporary values.

    **Correct strategy (per user):** noframe subs should NOT use the register
    allocator or the regfile at all. Instead, they should revert to **static
    in-memory variable allocation**, exactly like the 6502 codegen does and like
    the m68k codegen did before the register allocator was introduced. Named
    locals and temporaries get dedicated static variables in BSS (e.g.
    `p8v_varname`), accessed via absolute addressing. This is the natural fit
    for noframe subs because:
    - They manage the machine stack themselves, so they can't use A5 frames
    - They are typically simple (interrupt handlers, stack manipulation)
    - Static allocation avoids any register-pressure or spilling concerns
    - It matches the 6502 codegen's model, reducing codegen divergence

    This means 4.5's noframe strategy is NOT "hardware registers only" but
    rather "static memory variables" — the allocator is bypassed entirely and
    codegen uses the pre-allocator static-variable path. The register allocator
    stays skipped for noframe subs; only the codegen fallback changes from
    `p8_regfile` to static `p8v_` variables.

    This is actually **better for 4.5** because it removes the regfile
    dependency from noframe subs without requiring allocator changes. The
    static-variable path already exists in the codebase (used by 6502 codegen)
    and would need to be wired into the m68k codegen for noframe subs.
  The scope of 4.5 is larger than originally estimated — it requires
  refactoring init/startup code paths AND noframe subroutine handling, not
  just the allocator. The assembler would catch any missed reference as an
  undefined symbol.
- **Plan**: route unallocated vregs to frame slots (for frame-based subs) or
  to static variables (for noframe subs — see note above); emit the BSS labels
  conditionally - only when codegen fallback or a raw-`%asm` textual scan still
  references them, so hand-written library code keeps assembling; apply the
  same conditional to `p8_fregfile`; revisit the `VregRenumber` bail-out once
  the layout can be empty.

  The noframe case simplifies from "error if no frame possible" to "use static
  variables instead of regfile" — no allocator changes needed, only a codegen
  fallback change from `regAddr()` → `p8_regfile+N` to static `p8v_` variable
  addressing.
- **Benefit**: smaller BSS, no global mutable spill state (reentrant-safe),
  cleaner listings; the assembler catches any missed reference as an
  undefined symbol.
- **Effort**: medium, depends on 4.1/4.2.

### 4.6 Inliner purity gate [DONE]
- `inline sub` with parameters refused any non-`isSimple` argument, and
  `BinaryExpression.isSimple` is unconditionally false - so `cellidx(cx-1, cy)`
  never inlined (14 dead call sites in maze, each paying stack marshalling +
  `link/movem/unlk`).
- **Done**: new `isInlineableArgument` in `codeOptimizers/.../Inliner.kt`:
  everything historically accepted (`isSimple`, keeping pure builtins callable)
  plus any expression free of side effects and I/O reads. Applies on all
  targets and both single/multi-param paths. Two old-policy tests updated
  (`wrap(counter+1, 2)`, `cellidx(a+b, 20)` now inline); new test pins that
  truly effectful args (call results) still block. One C64 size table entry
  re-attributed (`wizzine` 1063 -> 1055: `sin8u(angle-spri*16)` inlined,
  subroutine gone).
- **Measured**: maze -61B (19576 -> 19515), all 14 `cellidx` calls gone with
  the subroutine dead-stripped; full `gradle build` green (2527 tests).
- **Follow-up spotted**: inlined bodies expose `move.b #8,d3` / `or.b d3,d2`
  shapes (load-const-then-operate, foldable to `ori.b #8,d2`) - candidate
  peephole/lowering item, not yet planned.

### 4.7 Avoid fixed A5 frame slots for simple locals [WON'T DO - NOT WORTH IT]
- **Problem**: named source-level locals (e.g. `ubyte ax` in `planet.distance()`)
  currently receive dedicated A5 frame slots at compile time and are stored to
  and reloaded from those slots around almost every operation, even inside a
  single basic block with no calls. Example: in `textelite` `distance()` the
  local `d` is stored to `-5(a5)` immediately after `sqrt` and then reloaded
  two instructions later just to shift it left by 2, when it could have stayed
  in the register that already held it.
- **Where**: `M68kRegisterAllocator`, `StackFrameLayout`, `AsmGen`.
- **Approach**:
  - Full solution: treat named locals as virtual registers alongside temporary
    vregs; run liveness analysis across the subroutine; allocate hot locals to
    hardware registers and spill only when necessary (register pressure,
    address-taken, live across calls, recursion/debugging requirements).
    Reserve dedicated A5 slots only for locals that must have a stable address.
  - Cheap fallback (attempted, then reverted - see below): add peephole passes
    for store-to-load forwarding within a basic block and dead-local-store
    elimination, without changing the frame model.
- **Benefit**: removes the ~23% of prog8 `move` instructions that currently
  hit A5 frame slots in `textelite` (392 of 1699 moves), reducing both code
  size and execution time.
- **Effort**: medium-high for the full register-allocation solution; small for
  the peephole fallback.
- **Trade-offs**: must preserve recursion, `&local`, `%option noframe`, and
  hand-written `%asm` semantics.

#### 4.7.1 Reverted peephole fallback - what went wrong (2026-11-03)

Do not resurrect the basic-block frame-slot pass as it was written; it produces
wrong code and its payoff is negligible (see the verdict below). Reference
implementation: commit `d6f512c` (removed again in `05200b4`, which is the
revert that was required to fix the miscompile). Because the pass was fully
reverted, `codeGenM68k` currently has no frame-slot round-trip optimization at
all.

- **What it did**: `AsmOptimizer.optimizeFrameSlotRoundTrips`, a linear scan over
  `pretrimmed`. It tracked the last store to each A5 displacement and removed
  (a) a store plus a later reload into the same data register when nothing
  clobbered that register in between, and (b) a store that a later store to the
  same slot overwrote before any read. Labels, branches, calls, and returns
  reset the tracked state.
- **The bug**: the tracked-register clobber test only ran on the non-move path.
  A frame-slot load (`move.b -4(a5),d2`) rewrites a data register but was
  handled by the "load from slot" branch, which only looked at
  `pending[srcSlot]` and never dropped pending stores whose `srcReg` was the
  load's destination. The pass therefore believed the register still held the
  stored value and deleted both the store and the reload.
- **Concrete miscompile**: `textelite` `goatsoup_rnd_number()` (`xx` in
  `-4(a5)`, `ac` in `-3(a5)`). The emitted code stores `ac`,
  reloads `xx` into the same D2, then returns `ac` from its slot. The pass saw
  `move.b d2,-3(a5)` ... `move.b -4(a5),d2` ... `move.b -3(a5),d2`, dropped the
  store and the reload, so the routine returned `xx` instead of `ac` - a silent
  wrong-result bug in galaxy generation, not a crash.
- **Why the tests missed it**: `TestInstructionSelectionOptimizations` only
  covered a clobber by a non-move (`addq.w #1,d2`), a branch boundary, a dead
  store, and label preservation. Nothing covered "the tracked register is
  rewritten by another frame-slot load", which is the shape the allocator
  actually emits. Only real output exposes it: textelite miscompiles, while
  maze's single removal (where the intervening load targeted a different
  register) happened to be correct.
- **Measured upside (why the revert cost nothing)**: compiling
  `examples/qemu68k/textelite.p8` and `maze.p8` at `d6f512c` vs `05200b4`
  removes exactly three 8-byte `move` pairs: two in textelite (one of them the
  miscompile above) and one correct pair in maze. That is -16 B on textelite,
  not the ~23% frame-slot traffic the full solution targets.
- **Verdict: NOT WORTH IT.** The actual measured improvement of this whole
  optimization is just 3 `move` pairs across both benchmark programs (and one
  of them was the bug). There is no reason to accept any correctness risk for
  that payoff; don't implement this peephole again.

#### 4.7.2 If a frame-slot peephole is ever attempted anyway

- Invert the clobber default: treat an instruction as clobbering every tracked
  register unless it is provably read-only for them. The deleted
  `instructionDestinationDataRegisters` was an incomplete allowlist missing
  `exg`, `bset`/`bclr`/`bchg` with a data-register destination,
  `abcd`/`sbcd`, `negx`/`nbcd`, `movem` reloads into D registers, `chk`, and
  the three-operand 68020 divide forms (`divsl`/`divul`, which fall through the
  two-operand check). Missing one entry silently miscompiles; being
  over-conservative only loses an optimization.
- Handle slot aliasing before trusting any slot state: an A5 value copied or
  offset into an address register (`move.l a5,a0`, `lea n(a5),a0`), overlapping
  slot widths that share a displacement, and address-taken locals read through
  a pointer.
- Keep the label/comment preservation and the basic-block barrier rules; they
  were the parts that worked.
- Verify with a real program, not only `gradle :codeGenM68k:test`: those tests
  assemble but never execute, so a wrong-value bug passes. Compile and diff
  `examples/qemu68k/{textelite,maze}.p8` (and an `amiga500` example) before and
  after, and run at least one of them (`qemu-system-m68k` or `-emu2`/vamos), or
  reason instruction by instruction about every removed store.
- Given the measured upside of tens of bytes, prefer the full vreg-based
  solution over another peephole.

### 2.8 Remaining easy peephole gaps [DONE]
- **Done**: `lsl.<size> #1,dN` -> `add.<size> dN,dN` for data registers. Done in
  `AsmOptimizer.optimizeLslOneToDouble`. N/Z/C/X are identical; V may differ
  (LSL clears V, ADD sets it on overflow), but generated code never reads V
  after a shift.
- **Done**: duplicate consecutive `ext.w`/`ext.l` into the same data register.
  Done in `AsmOptimizer.optimizeRedundantExt`.
- **Done**: `cmpi.<size> #0,dN` -> `tst.<size> dN` for data registers. Done in
  `AsmOptimizer.optimizeCmpZeroToTst`. `cmpi.<size> #0,Dn` is 4-6 bytes;
  `tst.<size> Dn` is 2 bytes, and the flags are identical for a zero
  comparison (N/Z reflect the register, V/C cleared). The current lowering
  does not emit zero-immediate comparisons in `textelite` or `maze`, so the
  pass is mainly defensive, but it is safe for any compiler-emitted or
  hand-written `cmp`/`cmpi` immediate-zero form.
- **Rejected**: `move.w X,dN` + `ext.l dN` and `ext.w dN` + `ext.l dN` folding.
  Existing tests/codegen rely on `ext.l` being emitted explicitly.
- **Rejected**: `moveq #0,dN` + `move.b X,dN` -> drop the `moveq`. This pair
  is the canonical M68K zero-extension idiom: `move.b` only writes bits 0..7,
  and `moveq #0` clears bits 8..31 so subsequent `.w` or `.l` operations see
  a correctly zero-extended value. It is emitted either directly by the
  lowering (`InstrArithmetic.kt`, `InstrLoadStore.kt`, `InstrControl.kt`,
  `AsmGen.kt`) or produced by `optimizeByteZeroExtendSafe` from
  `move.b M,dN` / `and.l #$ff,dN`. The `moveq` is load-bearing in all the
  real-program cases that look like candidates (e.g. `moveq #0,d2` /
  `move.b d3,d2` / `move.b (a0,d2.w),d3` needs the upper bits cleared for the
  word index). Removing it safely would require proving the consumer reads
  only the low byte and that no wider read intervenes before the next
  overwrite; that case is not worth a dedicated pass.
- **Where**: `AsmOptimizer.kt`.
- **Effort**: small-medium (each needs flag-safety scanning like 2.2/2.3).

## Tier 5 - Bigger structural work (months)

### 5.1 Better D0 reload cache
- Extend cache validity analysis across more opcodes and branch targets;
  currently invalidated conservatively.
- **Effort**: medium-high.

### 5.2 Dead store / dead variable elimination at IR level for m68k
- Tighten `UnusedCodeRemover` / `VregRenumber` for m68k-specific spill patterns.
- **Effort**: medium.

### 5.3 Pointer-indexed array access: keep index + scale in the IR memory reference
- **Problem**: pointer-indexed element access (`values[pos]` on `^^uword`,
  the shape the shared sorting lib and most generic-code loops use) is IR that
  pre-folds the element scale into a long index register:
  ```ir
  loadmz.w r80.l,[frame:-2]   ; r80 = zero-ext(pos)
  lsl.l     r80.l             ; scale-by-N folded in-register
  loadr.p   r81.p,r123.p      ; r81 = values
  addr.l    r80.l,r81.l       ; r81 += r80
  loadi.w   r82.w,[r81.p]     ; r82 = [r81]
  ```
  The backend then materializes the whole thing as ~9 instructions per access
  (zero-extend, double, two stagings, pointer stage + D0 bounce + add); on
  68020 it cannot use hardware scaling because the index/scale is gone. Per
  element read is 9 instructions today.
- **Fix**: teach the IR about a register-held pointer base with a scaled index,
  e.g. extend `MemoryReference.Indexed` to allow a `pointer`-vreg base (a new
  `AddressBase.Register(vreg)` variant) or add an indexed variant of
  `MemoryReference.Indirect`. Then:
  1. `ExpressionGen.translatePointerIndexing` and the deref-chain path emit
     `LOADI/STOREI [rPtr + rIdx.w * eltSize]` and let `multiplyByConst`/`lsl`
     disappear from the IR (do NOT pre-scale; keep scale in the memref just
     like the named-array `LOADX` path does).
  2. `resolveMemory()` lowers that shape directly. Caveat first: the
     `(rPtr, rIdx.w*scale)` word-index form sign-extends the 16-bit index, so
     an unsigned index (a `uword`/`ubyte` subscript is the normal case) at
     32768 or above would index backwards. It is only sound when the index is
     provably below 32768 (a byte index, or a bound the IR can state). Use a
     `.l` index for the general case: it still needs the `moveq #0` /
     `move.w` zero-extension (the `.l` form reads all 32 bits), so 68020 gets
     3 instructions per access that way, not 2. 68000: zero-extend index +
     manual scale + `add.l` (4-5 instructions). Either way the two
     now-redundant stagings disappear because the scaled effective address
     needs no An home.
  3. Scope/gate: the IR change touched `intermediate` is shared - so
     `IRTextCodec` encode/decode, the register-use accounting
     (`IRInstructions.addUsedRegistersCounts`), `mapRegisters`/renumber and the
     VirtualMachine interpreter must all handle the new shape. 6502 codegen
     does not consume IR so it is unaffected.
- **Benefit**: fewer instructions per pointer-indexed access: 9 -> 3 on 68020
  with a `.l` index (9 -> 2 only where a small unsigned index can be proven),
  ~1.7x on 68000 (9 -> 5); helps every m68k program using
  pointer-indexed loops, not just the sorting lib.
- **Effort**: large (IR representation + codec + VM + backend lowering).
- **Verification**: sort-lib bench on fixed arrays (sorted + random), plus
  `-dumpsymbols`-style corpus `.text` measurement over the examples; the
  `(aN,dN.w*2)` and `(aN,dN.l*scale)` encodings are 68020-gated and must be
  vasm-verified like 3.1 (5.3c already ships the `.l` form for this reason).

### 5.3b [DONE 2026-11-06] Backend peephole cleanup of the existing dance (option B)
- Two new passes in `AsmOptimizer.kt`, both running before the window passes:
  - `optimizeStagedAddCollapse`: `move.l aX,dY` / `move.l dY,d0` / `add.l d0,aN`
    -> `add.l aX,aN` (the pointer stage + D0-bounce trio), when the staged data
    registers are provably dead after the add and the trio carries no labels.
  - `optimizeRedundantStagedMove`: drop the first of two consecutive
    `movea.l dK,aN` stagings when the An is fully rewritten between them and
    never read (the dead stage in the index-doubling idiom).
- **Verified**: `gnomesort_uw` inner element read is now 6 instructions
  (was 9); full sorting test program `.text` 5029 -> 4813 bytes on qemu68k;
  all m68k example programs still assemble; qemu-system-m68k functional run
  of the m68k sorting example produces correct sorted output; full gradle
  build + `:codeGenM68k:test` pass.
- These shrink the 5.3 gap's 9-instruction baseline to 6; 5.3 remains the
  structural fix (and removes the remaining staging/`add.l` pairs on 68020).

### 5.3c [DONE 2026-11-06] Indexed-lea fold of the surviving sequence
- The 5.3b cleanup left a uniform tail per pointer-indexed access:
  `moveq #0,dN` / `move.w S,dN` / `add.l dN,dN` (or `lsl.l #2`) /
  `movea.l dN,aT` / `add.l aP,aT`. New pass `optimizeIndexedPointerLea`
  (registered with the target CPU; `optimizeAssembly` now takes `cpu`,
  default M68000) folds the last two (or three) instructions into one
  indexed lea:
  - 68020+: `lea (aP,dN.l*scale),aT` and the scale instruction is removed
    (hardware scaling, full `.l` index so the zero-extended word index
    cannot be sign-extended - the unsafe form would be `.w`).
  - 68000: scale instruction stays, stage+add become `lea (aP,dN.l),aT`
    (byte-neutral: 4B vs 4B, one instruction fewer).
  - Staged-base variants fold too: base staged through a data register
    (`move.l aP,dK; add.l dK,aT`) folds straight to the lea when dK is
    provably dead; base reloaded from memory (`move.l 10(a5),dK`) becomes
    `movea.l 10(a5),aT` + lea.
  - Flag safety: the removed `add.l` set flags, so the fold requires the
    next instruction to set flags itself (the element load/store `move`
    always does).
- Companion changes in the same session:
  - `InstrArithmetic` `ADDR`: address-register destinations now take a
    LONG/POINTER source directly (`add.l a2,a3`) instead of bouncing
    through d0 (`move.l a2,d2` / `move.l d2,d0` / `add.l d0,a3`).
  - `optimizeStagedAddPair`: two-instruction variant of 5.3b's trio
    collapse (`move.l <Dn/An>,dY` + `add.l dY,aN` -> `add.l <src>,aN`,
    staged reg provably dead).
  - `optimizeLoadIntoCompare`: `move.S X,dK` + `cmp.S dK,dM` ->
    `cmp.S X,dM` (and `cmpi` form) when dK is provably dead. Note: fires
    rarely in the sorting lib because the dominant shape there is
    load/cmp/branch and deadness stops at the control transfer.
  - Shared `provenDeadAfter` helper factored out of the three passes
    (bounded same-block scan: redefinition-before-read, else alive).
- **Verified**: sorting test `.text` on qemu68k 4813 -> 4669 bytes
  (5029 before this work started); per-access sequence down to 3
  instructions on 68020 (`moveq`/`move.w`/`lea (a2,d0.l*2),a3`) and 4 on
  68000. amiga500 `.text` 4770 bytes; vasm assembles both; qemu68k
  functional run sorts correctly; full `gradle build` green.
- Remaining in this shape: `movea.l d0,a3` / `move.l a2,d3` / `add.l d3,a3`
  where the staged d3 dies at the following conditional branch (handled by the
  bounded two-successor deadness scan in A.1), repeated `-2(a5)`/`8(a5)`
  reloads of `pos`/`num_elements` (IR-level liveness/allocator work, 5.2; see
  A.6), and the double `loadr.w` of the same value (r122/r77) in
  `shellsort_uw` (IR copy propagation; see A.6). 5.3 itself remains the
  structural fix that removes the `moveq`/`move.w` pair too.



## Assembly codegen improvements

Open asm-level findings from the 2026-11-07 inspection of the amiga500 sorting
benchmark (`examples/test.p8`, `.text` 4770 B; qemu68k 4669 B) after 5.3c.
All are `AsmOptimizer.kt` work unless noted. A.2 and A.4 are implemented;
A.3, A.5 and A.6 are not. A.1 and A.2 and A.4 are done. Together A.1-A.5 were worth roughly 80 B and
40-45 instructions there.

After A.2 + A.4: amiga500 `.text` 4770 -> 4722, qemu68k 4669 -> 4621 (-48 B
each). The passes remove 56 B of instructions; the difference is the
layout-dependent `align 2` (4-byte) and `align 4` (16-byte) padding moving
with the code. Verified with vasm (amiga500) and a qemu68k run of the sorting
test.

### A.1 Branch-aware deadness in `provenDeadAfter` [DONE]
- The helper bails on any control transfer, but the dominant sort-loop shape is
  `load / cmp / b<cond>` where the loaded register is dead on *both*
  successors. Extending the scan to follow a conditional branch's two
  successors (label index, recursive under the existing step bound,
  conservative on unknown) unlocks, in the sorting test alone:
  - 10x `move.w 8(a5),d3` / `cmp.w d3,d2` / `bhs` -> `cmp.w 8(a5),d2`
    (and the `move.w (a3),d3` / `cmp.w d3,d2` / `bhs` variant). ~30 B,
    10 instructions. The one shape `optimizeLoadIntoCompare` was measured not
    to fire on.
  - 8x the staged-base lea fold `optimizeIndexedPointerLea` cannot currently
    prove: `movea.l d0,a3` / `move.l a2,d3` / `add.l d3,a3` ->
    `lea (a2,d0.l),a3` (2 B and 2 instructions per site), including the
    `move.l 10(a5),d6` reload variant -> `movea.l 10(a5),a2` + lea (byte-
    neutral on 68000, 1 instruction). All 8 also lose the scale instruction on
    68020. ~10 instructions total.
  - 3 of A.3's 4 sites.
- This supersedes the old "all need cross-block proof, not more peepholes"
  conclusion for the staged-add-at-branch case: the proof is local, just
  two-sided. The repeated frame-slot reloads and the double `loadr.w` from that
  same paragraph really do need 5.2 / allocator work (see A.6).
- **Safety**: unchanged rules - redefinition before read on every path,
  `rts`/`unlk` end a path as dead, anything unparseable goes alive; recursion
  is bounded by the same step cap.
- **Effort**: small. Also unlocks more of `optimizeStagedAddPair` and
  `optimizeLoadIntoCompare` in other programs with the same loop shape.

- DONE: `provenDeadAfter` now follows conditional branches (dead only if dead on
  both successors) and unconditional `bra` (follows target); `rts`/`rtr`/`rte`
  end a path as dead. Label stripping lets branch targets be scanned. Measured
  on the sorting benchmark: amiga500 `.text` 4722 -> 4690, qemu68k 4621 -> 4589
  (-32 B each), matching the predicted ~30 B (10 move.w->cmp.w folds) plus
  cascades. Sorting test still prints all eight lists correctly.

### A.2 Dead zero-extend writeback [DONE]
- 11x in the sorting test:
  ```
  moveq  #0,d0
  move.b  -1(a5),d0
  move.w  d0,d2        ; dead: the consumer reads d0 through the d0 cache
  add.w   d0,d0
  ```
  `emitStoreD0` always writes the value back to the vreg's home register, even
  when the next instruction consumes it via the d0 cache (the vreg is live; it
  is the home-register copy that is dead). Dropping the third instruction saves
  1 instruction / 2 B per site. 10 of the 11 are dead; the live one is in
  `print_l` and belongs to A.3. All 11 are followed by an instruction that sets
  flags.
- DONE: `optimizeDeadZeroExtendWriteback` matches `moveq #0,d0` /
  `move.b|w S,d0` / `move.b|w|l d0,dK`, requires the next instruction to define
  N/Z/V/C itself, and proves dK dead with a scan that may cross labels and
  calls (`registerUnreadBeforeRedefinition`). The 7 main-loop sites sit on the
  other side of a `bsr` before dK is redefined, which the plain single-block
  scan (`provenDeadAfter`) refuses; the 3 shellsort sites fold with either scan.
  Measured: 10 sites, 20 B of instructions.
- Not to be confused with: 2.8's rejected `moveq`-drop (the `moveq` stays
  here), 5.1 (d0 cache validity - this elides the writeback instead), or the
  reverted 4.7.1 frame-slot pass (nothing is tracked across slots).
- **Where**: `AsmOptimizer.kt`. Skipping the writeback in `emitStoreD0` would
  need liveness plumbed into the lowering, so the asm pass is the cheaper route.
- **Effort**: small.

### A.3 Source staging before a store [OPEN]
- 4x:
  ```
  move.l d0,d2 / move.l d2,p8b_txt.p8v_fmt_value_l -> move.l d0,p8b_txt.p8v_fmt_value_l
  move.w d4,d5 / move.w d5,-4(a5)                  -> move.w d4,-4(a5)
  ```
  2.7 (`optimizeConstOperateFold`) folds the constant-first-line variant only;
  2.6 is the opposite direction (store then reload). 1 of the 4 folds with the
  current scan, the other 3 need A.1.
- **Effort**: small (same pass family as A.2).

### A.4 `pea` for stack pointer arguments [DONE]
- 9x in `main`'s call setup: `move.l #p8v_vals,d2` ... `move.l d2,-(sp)`
  -> `pea p8v_vals`. The assembler encodes the `pea` PC-relative (4 B), so a
  site drops from 8 B to 4 B, one instruction fewer.
- DONE: `optimizePeaForStackArgs` folds only when a symbol is pushed within
  three instructions, no other instruction in between mentions the staged
  register, an intervening instruction defines all flags the removed move set,
  and the staged register is not read before its next definition. The `pea` is
  emitted at the push position (not where the value was materialized), so the
  argument push order is unchanged. Measured: all 9 sites, 36 B of instructions.
- 3.7 deliberately covers hardware-register slots only and leaves stack args as
  registers; this is that leftover. `pea` never appeared in generated code
  before this pass.
- **Effort**: small.

### A.5 Branch-to-branch chain retargeting [OPEN, runtime only]
- 6x, all in the gnomesort tail: `bra p8_label_gen_23` where that block is
  itself `bra p8_label_gen_21`, which is `bra <whileloop>` - 2 extra branch
  executions per inner-loop iteration. Retarget each `bra` to the terminal
  target.
- Saves no bytes: the intermediate labels are also reached by fall-through, so
  they cannot be dropped. 3.4 (DONE) covers conditional-over-bra, `bsr`+`rts`
  and PC-relative `jmp`, not this shape.
- **Effort**: small (follow a `bra` target to a block whose first instruction is
  another `bra`; bounded).

### A.6 Frame-slot traffic and the base-pointer clobber [measurement, feeds 4.3/5.2]
- The six sorting routines contain 112 frame-slot loads and 15 stores; the hot
  ones are `-2(a5)`/`-4(a5)`/`-6(a5)` (loop position), `8(a5)`
  (`num_elements`) and the pointer arguments (`10(a5)`/`14(a5)`), reloaded per
  statement and per loop test. Only 5 are provably redundant under a
  conservative scan, because the temporary holding them is clobbered by the
  next statement. Value residency across statements is 5.2/4.3 work, not a
  peephole.
- Concrete cause of the remaining base reload in the shellsort inner loop: the
  element address is computed *into a2*, clobbering the base pointer a2
  already holds, so the base is reloaded (`move.l 10(a5),d6`) before every
  access. An address-register assignment that keeps the base in one An and
  puts the target in another removes the reload (and the surviving
  `movea`/`lea` fold pair with it). A.1 only makes the folded shape cheaper; it
  does not remove the reload.
- Also recorded in 5.3c: the double `loadr.w` (`move.w d3,d2` /
  `move.w d3,d4` for the same vreg value, 3x) needs copy coalescing in the
  allocator, i.e. letting the two vregs share one register while their live
  ranges overlap and the value is untouched.

## Measurement

- Do NOT use `-compareir` for this work: it compares `.p8ir` files, and
  backend peephole / instruction-selection changes leave the IR untouched.
  Only relevant for true IR-level work such as 5.2.
- Use emitted `.asm` code-line counts (exclude comment lines) and section
  sizes of a representative set on each m68k target (e.g.
  `examples/qemu68k/textelite.p8`, `examples/qemu68k/maze.p8`) before/after.
- There is no m68k expected-size table in `TestCompilerOnExamples` (classes
  cover C64 / Cx16 / Both / Virtual / PET32 only); update exhaustiveness
  accordingly rather than assuming AGENTS.md's expected-size guidance applies.
- Run the codeGenM68k unit tests after each tier (`gradle :codeGenM68k:test`,
  no vasm required with `assemble=false`).
- Build discipline (full builds are slow): per-tier verification is
  `gradle :codeGenM68k:test` only. Rebuild + reinstall (`installdist`) solely
  to run end-to-end `prog8c` checks on real programs. Full `gradle build`
  only before final verification or when touching shared modules (compiler,
  intermediate, codeCore). Docs/ideas-only edits need no build at all.
- Docs: pure codegen-quality changes need no doc updates, and symboldumps are
  unaffected (they record stdlib signatures, not codegen output). But any new
  user-facing flag or `%option` (e.g. the `-printirasm` flag in 1.1) must add
  its `docs/source` entry alongside the implementation.
