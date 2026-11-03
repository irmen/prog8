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

### 3.1 Use 68020 scaled indexing directly [DONE]
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

### 3.5 Multiply-by-constant strength reduction
- Replace `mulu.w #N` / `muls.w #N` (and `.l` forms on 68020) with
  shift-add-sub recipes where cheaper. The recipes work for both signed and
  unsigned multiplication by a positive constant: left-shift and add are
  linear over two's complement, so the same `x << a + x << b + ...` chain
  produces the correct low bits for `muls` as well as `mulu`. Negative
  constants (`x * -N`) can be handled by computing the positive recipe and
  negating the result.
- Examples: few-bit constants (`10x = (x<<3)+(x<<1)`), subtraction forms
  (`29x = (x<<5)-(x<<2)-x`, `31x = (x<<5)-x`), factored forms (`45x` via
  `9x` then `5x`). Classic minimal-chain recipes (Bernstein); a small table
  of common strides (array widths, screen rows, struct sizes) covers nearly
  all real occurrences.
- **Where**: lowering where `mulu.w #N` is emitted (`InstrArithmetic.kt`,
  index scaling in `resolveMemory()`), possibly plus a peephole for
  already-emitted forms.
- **Gate by CPU and term count**: aggressive table on 68000 (`mulu.w` is ~70
  cycles, nearly every 2-3 term recipe wins; allow up to ~3-4 terms);
  short recipes only on 68020 (`muls.l` is ~28-30 cycles, chains run ~8-16;
  limit to ~2 terms). Off on 68030/68040/68060 because the hardware
  multiplier is fast enough that shift-add chains are unlikely to help and
  can be slower. Prog8 currently has no 030/040/060 targets, so the practical
  cutoff is 68000 vs 68020, but the implementation must check
  `compTarget.cpu` and disable the optimization for anything newer than 68020.
- **Benefit**: speed first, size second. Real occurrences exist: `mulu.w #29`
  in `cellidx` and float-index paths. Baseline: vbcc `-O2` also emits
  `mulu.w #29` here, so this is a spot to beat vbcc, not just match it -
  measure against the `available_uncarved` comparison setup.
- **Effort**: medium.
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

## Tier 4 - Register allocation & spilling (weeks)

> Re-verify this tier's premise first: real output shows spills already go
> primarily to A5 frame slots (M9 promotion) and regfile traffic is nearly
> nil even on large examples. The "largest quality gap" framing below may be
> outdated; re-measure on higher-register-pressure programs before investing.

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

### 4.2 Prefer frames over regfile as the spill target
- Make the regfile a last resort (globals / non-reentrant state) rather than
  the default fallback for uncolored vregs.
- **Effort**: medium, depends on 4.1.
- **Does NOT remove the regfile**: this only reduces traffic. Some cases still
  legitimately need the regfile fallback (unallocated vregs, `%option noframe`
  subs, raw `%asm` textual references, float spills). Actual removal of the
  `p8_regfile` / `p8_fregfile` BSS labels is 4.5 and must remain conditional.

### 4.3 Improve coloring heuristic
- Current greedy by liveness. Add cost model: prefer keeping pointer-typed
  vregs in address regs (A2-A4), data in D2-D6; spill cheapest (fewest uses,
  longest live range) first.
- **Effort**: medium.

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
  unallocated vregs (empty allocator map), `%option noframe` subs (cannot take
  frame slots today), raw `%asm` textual refs (`inlineAsmMayReadRegisters`
  can only see the regfile symbols textually), float spills.
- **Plan**: route unallocated vregs to frame slots (or keep the noframe error
  when no frame is possible); emit the BSS labels conditionally - only when
  codegen fallback or a raw-`%asm` textual scan still references them, so
  hand-written library code keeps assembling; apply the same conditional to
  `p8_fregfile`; revisit the `VregRenumber` bail-out once the layout can be
  empty.
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
