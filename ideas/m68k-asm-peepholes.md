# M68k Assembly Peepholes

Plan for the window-based peepholes in `codeGenM68k/src/prog8/codegen/m68k/AsmOptimizer.kt`.
Only rewrites that a peephole pass can do over one or a few generated instructions belong
here. The allocator, `AsmGen` and backend-emitter items that were found in the compiler
document are listed under "Moved items" below. Opportunities that need a change in the IR
codegen or the AST are in `m68k-compiler-optimizations.md`.

Related design: `m68k-register-allocation.md`.

**Status:** Draft, updated to the current compiler state (Oct 2026). Review is done;
implementation has not started. Baseline measured on `amiga500` (section "Baseline").

## Current state

- The register allocator is the only m68k code path. Stage 3 (teardown) and Stage 4
  (default allocator) are complete. There is no command-line switch, flag, or golden
  harness for the old path.
- `%option noframe` subroutines are never allocated and still use `p8_regfile` round
  trips (for example the amiga startup `init_system`, about 5 bounce pairs per amiga
  program, accepted in Stage 4). So `p8_regfile` still appears in the output, and the
  peephole rules must still handle it where it is emitted.
- `AsmOptimizer.kt` contains four passes. Stage 3 removed the regfile-specific
  peepholes, so there are no pending deletions to coordinate with.
- The earlier `amiga-regalloc/` snapshot that the first version of this plan was
  based on is not in the repository. Its counts cannot be reproduced. The counts in
  this document come from the current tree (see "Baseline").

## Background

The existing `optimizeAssembly()` in
`codeGenM68k/src/prog8/codegen/m68k/AsmOptimizer.kt` runs four passes:
jump-to-next-label, tail call (`bsr` + `rts` to `bra`), redundant `tst` after a
flag-setting move, and redundant `lea base,a0`. None of them looks at data flow
between registers or frame slots.

Reading the generated code shows the following patterns:

- Frame locals are stored and reloaded with no change in between
  (`move.b d3,-13(a5)` followed by `move.b -13(a5),d3`).
- Values are copied through a temporary register before use
  (`move.b -6(a5),d5` / `moveq #0,d6` / `move.b d5,d6`).
- Small constants are loaded with `move.w`/`move.l` where `moveq` is shorter and
  faster.
- Address constants go through a data register before being moved to an address
  register.
- Self-moves and swap-back copies remain in the output.

## Priorities and acceptance

- **Fast code over small code.** When a rewrite trades size for speed, take the
  speed. Each rewrite should be justified with a cycle estimate for the 68000
  (`amiga500`), not only a byte count. For example, `moveq #N,Dn` is 4 cycles and
  `move.w #N,Dn` is 8 cycles on the 68000.
- **Acceptance:** all example programs run correctly, and the total output size is at
  most 2% larger than the baseline. Tier A passes only remove or shorten instructions,
  so the size limit is unlikely to bind for them. It matters for later codegen changes.

## Design rule

`m68k-register-allocation.md` §0 says that improvements must come from the codegen
structure. Peepholes are allowed for local rewrites that are cheap to verify. A
peephole must not compensate for a missing allocator feature.

- **peephole**: local rewrite over one or two instructions, or a block-local liveness
  check. Implemented in `AsmOptimizer.kt`. Every item in this document has this owner.
- Anything that needs an AST or IR codegen change is listed in
  `m68k-compiler-optimizations.md`. Allocator, AsmGen and backend-emitter items are
  listed under "Moved items" below.

Peephole passes must not depend on `p8_regfile` layout for allocated code. The
passes must still match the `p8_regfile` forms that `noframe` subroutines emit, or
leave them alone.

## Baseline

Measured on the current tree with `prog8c -target amiga500` over `examples/amiga/*.p8`.
The compiler-side document refers to these numbers.

- 20 programs compile. `ahimodes`, `ahiplay` and `stream-adpcm` are excluded: they
  import an `ahi` module that is not in the library path.
- Instructions: counted by mnemonic in the generated `.asm` files, so the count is
  approximate. Size: the linked executable.
- `qemu68k` is not measured yet.

| Program | Instructions | Bytes |
| --- | ---: | ---: |
| 3d | 1796 | 10060 |
| adpcmbench | 2142 | 16592 |
| bdmusic | 1273 | 7028 |
| blitcop | 1266 | 7944 |
| circles | 1150 | 6656 |
| fractal-tree | 1058 | 7016 |
| gfxdemo | 1808 | 8704 |
| hlaudio | 1284 | 12112 |
| iff | 1158 | 6068 |
| izx0reader | 885 | 4492 |
| lowlevel-timer | 833 | 4680 |
| modplay | 2321 | 13240 |
| rasters | 708 | 3780 |
| screen | 555 | 3404 |
| stream-disk | 1670 | 8584 |
| stream-file | 1380 | 7320 |
| tictactoe | 1554 | 7768 |
| wavc | 2089 | 16256 |
| window3d | 1300 | 8104 |
| window | 877 | 4932 |
| **Total** | **27107** | **164740** |

Raw data: `/tmp/opencode/baseline/summary.txt` (scratch, not in the repository).

## Inventory

Counts are from the current tree unless marked "not re-measured". "Raw" counts are
text matches before the rewrite's conditions are applied.

### Tier A - window-2 peepholes (no liveness needed)

| ID | Pattern | Rewrite | Count | Status |
| --- | --- | --- | --- | --- |
| A1 | `move.S dN,M` / `move.S M,dN` | drop the reload | 0 (eliminated by F1+allocator) | NOT WORTH IT (trivially 0) |
| A2 | `move.S dN,M` / `move.S M,dK` (K != N) | keep the store, replace reload with `move.S dN,dK` | 35 (23 programs) | open |
| A3 | `move.S dA,dB` / `move.S dB,dA` | drop the second move | 46 (23 programs) | open |
| A4 | `move.S dN,dN` | drop the self-move (see safety) | 14 | DO NOT IMPLEMENT |
| A5 | `move.w/l #N,dK`, N in -128..127 (or $FF80..$FFFF for .w) | `moveq #N,dK` | 644 raw `move.w`/`move.l #imm,dN`; in-range subset not re-counted | IMPLEMENTED (.l at emitter, .w via peephole) |
| A6 | `move.b M,dN` / `and.l #$ff,dN` | `moveq #0,dN` / `move.b M,dN` | 17 (10 programs) | open |
| A7 | `bCC L1` / `bra L2` / `L1:` | `bCC' L2` | about 14 `bCC` then `bra` pairs (rough; exact shape not checked) | IMPLEMENTED |
| A8 | moved to "Moved items" (M2, AsmGen) | - | - | moved |
| A9 | `bra L` where `L:` is `rts`, or `unlk a5` then `rts` | copy the return sequence in place of the `bra` | 35 | NOT WORTH IT |

A6 has a better fix outside the peephole: an explicit zero-extension in the IR (see
`m68k-compiler-optimizations.md`, F8). This peephole is the fallback for the cases that
still reach the backend.

Safety notes:

- **A1, A2, A3**: `move.b` to a data register writes only the low byte, and a byte
  store to memory writes only the low byte. The reload and the register copy give
  the same value and the same N/Z flags. Keep labels on the removed line (the existing
  `applyModifications` already does this).
- **Memory operand rule (A1, A2).** The existing `isSafeMemoryOperand()` rejects any
  operand with parentheses, so it also rejects frame slots such as `-6(a5)`. A1 and A2
  target exactly those slots, so they need their own predicate. Allow `N(a5)` frame
  slots and named symbols. Reject `-(aN)`, `(aN)+`, indexed addressing, and absolute
  hardware addresses (for example `$dff180`). Reading custom-chip or CIA registers can
  have side effects.
- **A3 (swap-back)** also needs the memory rule if one of the operands is memory. The
  register-only form is always safe.
- **A4**: a move sets N and Z from the value. Removing it changes the flags, so the
  rewrite is only safe if the next instruction sets N and Z itself (`move`, `moveq`,
  `tst`, `clr`) or does not read the flags. Check the next instruction, which needs a
  window of 3.
- **A5**: `moveq` sets all 32 bits. `move.w` and `move.b` leave the upper bits alone.
  The upper bits are not verified to be unused by later reads. For the `.w` form, either
  check that the next reads of the register are `.w` or `.b` operations, or leave the
  `.w` form out. Flags match: N and Z depend only on the value, and both instructions
  clear V and C. Restrict to the range where the sign-extended value equals the
  immediate.
- **A6**: `and.l` and `moveq` both clear V and C and set N and Z from the 32-bit result,
  which equals the byte result because the upper bits are zero. The rewrite also needs
  the check that `M` does not use `dN` (see B1). Clearing `dN` before loading `M` would
  otherwise change the address when `M` is indexed by `dN`, for example `(a0,d1)`.
- **A7**: exact inversion table needed for each condition code. Only one occurrence
  was found earlier, so it may not be worth implementing.
- **A9**: the copied return sequence must be only `unlk a5` and `rts` (or `movem.l
  (sp)+,...` followed by `unlk a5` and `rts`). The label that the `bra` targets stays
  in place for other branches. A copy does not change flags or registers.

### Tier B - block-local liveness needed

| ID | Pattern | Rewrite | Count | Status |
| --- | --- | --- | --- | --- |
| B1 | `move.b M,dA` / `moveq #0,dB` / `move.b dA,dB` | `moveq #0,dB` / `move.b M,dB` when dA is dead and `M` does not use `dB` | 21 (14 programs) | open |
| B2 | `move.l #sym,dN` / `movea.l dN,aM` | `movea.l #sym,aM` when dN is dead | 0 (eliminated by M1) | NOT WORTH IT (trivially 0) |
| B2b | `move.l M,dN` / `movea.l dN,aM` | `movea.l M,aM` when dN is dead | 11 (4 programs) | open |
| B3 | `lea X,aN` / `movea.l aN,aM` | `lea X,aM` when aN is dead | 11 (6 programs) | open |

B1b (`move.S M,dA` / `move.S dA,dB`) is not a peephole. It is argument setup that the
allocator should remove, and it is listed as M5 under "Moved items".

B1 is the rewrite that the IR load/extend fusion makes much larger (compiler document,
F3). On its own it only removes one copy per site.

**Liveness definition.** A register is dead after an instruction if the next access
in the same basic block is a full write. Only these write the whole register:

- Data registers: `move.l`, `moveq`, `clr.l`, `and`/`or`/`eor` on `.l`, and `add.l`
  and similar `.l` operations that read the register first.
- Address registers: `movea.l`, `lea`, `move.l` and `add`/`sub` on `.l` after a read.

`move.b`, `move.w`, `clr.b`, `clr.w`, and `addq`/`subq` on `.b` or `.w` only write the
low bits, so they do not kill liveness. Labels, branches, calls and `rts` end the
block, and a register is treated as live at those points. This is conservative and
needs no whole-program analysis.

**B1 safety.** The rewrite clears `dB` before `M` is loaded. If `M` uses `dB` as an
address or index register, the result is wrong. Reject the rewrite in that case. The
same check applies to A6.

B2 changes the flags: the original `move.l` sets N and Z on the address value, while
`movea` does not. Confirm that no following branch depends on those flags.

## Moved items (allocator, AsmGen and backend owners)

These are not peepholes. Their fix belongs to the register allocator, `AsmGen` or the
backend emitters, not to AST processing or IR codegen. A peephole can only mask part of
each one, so the owner should make the change. Counts are from the baseline scan
(approximate). M9 (local promotion) is an IR change, but it only pays off with the
register allocator, so it is listed here too.

- **M1 - argument register hints (allocator) - IMPLEMENTED.** Pointer through a data register:
  `move.l #sym,dN` / `movea.l dN,aM` (180 raw, 0 remaining after M1) and `move.l M,dN` /
  `movea.l dN,aM` (296 raw, 11 remaining after M1). The most common pattern in the scan. Example
  `adpcmbench.asm:1359-1364`, from `txt.print("...")` (`adpcmbench.p8:28`). IR:
  `load.p r222.p,#prog8_interned_strings.string_1`, then
  `call p8b_txt.p8s_print(r222.p@s18)`. A pointer vreg used only as an `a0` argument
  should be allocated to `a0`. Peepholes B2 and B2b are partial fixes. The same hints
  remove the call-setup copies and self-moves (A3, A4, B1b). Example: `move.w d2,d2` /
  `move.w d3,d3` before `bsr blitter.line_draw` in `circles`.
- **M2 - empty `run_global_inits` (AsmGen) - CLOSED-NOT WORTH IT.** `bsr run_global_inits` in the program entry
  point, and a `run_global_inits:` body that is only `rts`. Occurs in 15 programs (the
  old peephole item A8). `AsmGen.kt:1007-1009` always emits the label and the `rts`, and
  `AsmGen.kt:1058-1059` always emits the call for `p8b_main.p8s_start`. Fix: skip both
  when `program.globalInits` is empty. This changes the output for 15 programs, so it
  needs a decision (open question 2).
- **M3 - zero-extended copy coalescing (allocator) - NOT WORTH IT.** The allocator part of F3:
  `moveq #0,dB` / `move.b dA,dB` (404 raw, 69 remaining after F3 fusion). The allocator should place the result of
  `loadm.b` in the register that the following `ext.b` writes. The IR fusion
  (compiler document, item 2) removes most of these. Peephole B1 is the fallback.
- **M4 - sign-extension copy coalescing (allocator) - NOT WORTH IT.** The allocator part of F5:
  `move.b d2,d3` / `ext.w d3` (32 matches, `window3d.asm:3863-3865`). The IR fusion is
  in the compiler document (F5).
- **M5 - argument setup copies (allocator).** `move.S M,dA` / `move.S dA,dB` (was B1b).
  Coalescing in the allocator should remove it. Re-measure against the current output
  before adding a peephole fallback.
- **M6 - constant shift count via register (backend emitter).** `move.w #15,d1` then
  `asr.l d1,d2`. Shift immediates only go to 8. Hoisting the count load is a codegen
  matter. The `moveq` form of small constant loads is peephole A5.
- **M7 - library base reload (AsmGen or backend emitter).** `move.l sys.IntuitionBase,a6`
  before each library call. An absolute load of 20 cycles on the 68000. Caching it needs
  the library ABI to preserve A6, and proof that the library base globals are not written
  after startup. Verify in the NDK docs before any change.
- **M8 - epilogue tail duplication - NOT WORTH IT.** `movem.l (sp)+`,
  `unlk a5`, `rts` shared by several branches. Requires CFG block reordering to share
  the full epilogue across paths with identical stack/register-save state; the simpler
  precursor (A9, single `bra` to a return block) was already closed as not worth the
  complexity. Not analysed further.
- **M9 - local promotion to vregs (IR, allocator-dependent) - COMPLEX.** Plan document:
  `ideas/m68k-m9-local-promotion.md`. Promote non-escaping
  scalar locals and parameters to vregs before `StackFrameLayout`. This is the largest
  opportunity on the register-allocation branch. Earlier snapshots had 1,843 loads from
  frame locals and parameters, 193 loads from locals of static subroutines, and 334
  frame reloads of a value stored earlier in the same chunk. Eligibility can reuse the
  `StackFrameLayout` checks: not address-taken, no asm reference, not static-initialised,
  not explicitly aligned. The loop counter case in F1 (`addq.b #1,-5(a5)`) is the same
  family. On `master`, vregs get frame slots (`frameVregSlots` in `AsmGen.kt`), so
  promotion alone only moves a local from one frame slot to another. It needs the
  allocator to pay off. Removes: A1, A2 (peephole), C2 and most of the reload and copy
  chains. The 6502 effect is unknown, since vregs are not obviously cheaper than static
  cells there.

## Work

1. **Open questions.** See below. The answered ones are kept for reference.
2. **Baseline.** Done for `amiga500` (20 programs, see "Baseline"). `qemu68k` not yet
   measured. Re-run the same measurement after each pass.
3. **Tier A passes.** Add one pass per item to `AsmOptimizer.kt`, each as a window-2
   (or window-3 for A4) function registered in `optimizeAssembly()`. Add unit tests
   next to the existing peephole tests in
   `codeGenM68k/test/TestInstructionSelectionOptimizations.kt`, covering: a matching
   case, a label on the second line, a flag-reading next instruction (A4), a memory
   operand that uses the destination register (A6, B1), and a non-matching register or
   size.
4. **Liveness helper.** Add a block-local liveness function over the `TrimmedLine`
   window, using the full-write definition above, then the tier B passes on top of it.
   Keep the helper separate so it can be reused for allocator checks.
5. **Verification.** After each pass:
   - `gradle :codeGenM68k:test --console=plain` for the pass tests.
   - `gradle installdist installshadowdist --console=plain` to install the compiler.
   - Recompile the example set, compare instruction counts and sizes with the baseline,
     and check the 2% acceptance limit.
   - Run the programs on `amiga500`. Vamos (`-emu2`) is not installed on the current
     machine. FS-UAE needs a Kickstart 2.0 or later ROM, because some examples use
     tag-list functions that Kickstart 1.3 does not have.
   - `qemu68k` cannot run the Amiga examples, because they call AmigaOS libraries. Use
     separate non-Amiga test programs for that target.
   - Full `gradle build` before the final verification.

## Open questions

Answered:

- **Snapshot and allocator.** The allocator is the default and the only path. Stage 3 is
  complete, so there are no pending regfile peephole deletions.
- **Tier B scope.** The allocator exists, so tier B is re-measured against current
  output. It is not blocked on the allocator.
- **Target priority.** Fast code over small code.
- **Acceptance.** Correct output and at most 2% larger than the baseline.

Still open:

(none)

Answered (added):

- **Local promotion scope (M9).** RESOLVED: plan document created at
  `ideas/m68k-m9-local-promotion.md`. Recommendation: implement for m68k first,
  measure 6502 separately, gate behind `is68k` if 6502 shows no benefit.
