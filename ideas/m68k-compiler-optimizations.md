# M68k Compiler Optimization Opportunities

Opportunities whose fix is in AST processing or IR codegen (`compilerAst`, `astprocessing`,
`codeGenIntermediate`, including `IRPeepholeOptimizer` and `StackFrameLayout`), and that
apply to the `master` branch, which has no m68k register allocator. Items that depend on
the allocator are in `m68k-asm-peepholes.md`, section "Moved items". The peephole
rewrites are in that document too, and it holds the baseline measurements used here.

Related design: `m68k-register-allocation.md`.

**Status:** Draft, updated to the current compiler state (Oct 2026). Findings are
recorded. One item (F2, signed division by a power of two) is implemented as an
uncommitted working-tree change; all other items are unimplemented.

## Pipeline and owners

Pipeline for m68k: AST (`compiler/src/prog8/compiler/astprocessing`) -> `IRCodeGen`
with `IRPeepholeOptimizer` -> `M68kCodeGenerator`: `StackFrameLayout`, then
`M68kRegisterAllocator` (in `RegisterAllocator.kt`), then `AsmGen`, then `AsmOptimizer`.
The `.p8ir` output is written after `StackFrameLayout` and before allocation, so it
shows frame locals and the zeroing prologue as the allocator sees them. On `master`
there is no allocator stage: `AsmGen` places vregs in frame slots (`frameVregSlots`).

Owners used below:

- **AST processing**: `compilerAst`, `codeOptimizers`, `astprocessing`.
- **IR codegen**: `codeGenIntermediate`, including `IRPeepholeOptimizer`,
  `IRCodeGen` and `StackFrameLayout`.

Where a family has both an IR part and an allocator part, only the IR part is here. The
allocator part is in the peephole document under "Moved items".

## Summary of the re-scan

The 20 baseline `.asm` files were re-scanned for patterns beyond the peephole tiers.
Each pattern was traced to the IR (the `; ` comments above each asm group, and the
`.p8ir` files) and then to the Prog8 source. The counts come from scratch awk scanners
in `/tmp/opencode/` (not in the repo). They are approximate, and the redundant-load
scanner is conservative: it resets its state at labels, calls, unconditional branches
and any store to memory.

| Family | Asm pattern | Count (baseline) | Owner |
| --- | --- | ---: | --- |
| F1 | store then reload of the same variable (redundant loads) | 150 drop, 165 copy (all distances); 172 IR-adjacent `storem`/`loadm` pairs | IR codegen (`foldStoremLoadmToLoadr`) |
| F2 | `ext.l` + `divs.w #2,dN` (signed division by a power of two) | 8 | IR codegen (`divideByConst`) - implemented, see its section |
| F3 | `moveq #0,dB` + `move.b dA,dB` (zero-extended register copy) | 404 | IR codegen (load/ext fusion) |
| F5 | `move.b dA,dB` + `ext.w dB` (sign-extension after a byte copy) | 32 | IR codegen (fuse with the byte load) |
| F8 | `and.l #$ff,dN` before `divu.w` | 21 | IR (explicit extension, ranked item 3) |

F4 (pointer through a data register, allocator), F6 (empty `run_global_inits`, AsmGen),
the allocator parts of F3 and F5, and local promotion to vregs are in the peephole
document under "Moved items" (M1, M2, M3, M4 and M9). F7 (unconditional `bra` to a return
block, 35 matches) is a local rewrite, so it is item A9 in `m68k-asm-peepholes.md`.

Conditional branches to a return block (330 by this scan) are not counted as
opportunities: nearly all of them skip the body of an `if` that ends the subroutine.

## Findings by family

### F1 - store then reload (IR codegen)

- Asm: `move.l d2,-8(a5)` then `move.l -8(a5),d2` (`tictactoe.asm:3637-3640`);
  `move.l d4,dos.read_file.lock` then `move.l dos.read_file.lock,d2`
  (`adpcmbench.asm:3111-3113`).
- IR: adjacent `storem.p r210.p,[frame:-8]` and `loadm.p r211.p,[frame:-8]`.
  172 such adjacent pairs across the baseline.
- Source: `^^graphics.RastPort rp = easygfx.rastport` followed by
  `void graphics.SetFont(rp, font)` (`tictactoe.p8:43-44`). The local is written to its
  frame slot and read back in the next statement.
- Why it stays: `IRPeepholeOptimizer.kt:955-967` only removes a `loadm` followed by a
  `storem` of the same memory. The reverse order (store, then load) is handled only by
  `foldStoremLoadmToLoadr`, which is disabled (`IRPeepholeOptimizer.kt:8-15`). Its
  reason was the cost of regfile spills. On `master` the `p8_regfile` path is the
  default, so that cost argument applies directly. On the register-allocation branch the
  fold would need re-measuring on the allocator path.
- Same family: the `for index in 0 to 20` loop counter in frame memory
  (`addq.b #1,-5(a5)` then `move.b -5(a5),d2`, `window3d` and `circles`). Its fix is
  promotion, which is M9 in the peephole document.
- Owner: IR codegen. The AST is correct here, and it has no register concept.
- AST processing: no change needed.

### F2 - signed division by a power of two (IR codegen) - implemented

- Asm: `ext.l d2` then `divs.w #2,d2` (`window3d.asm:3952-3953`; 8 sites in fractal-tree,
  gfxdemo, window3d and tictactoe).
- IR: `divs.w r309.w,#2.w`, emitted by `IRCodeGen.divideByConst`
  (`IRCodeGen.kt:1312-1348`). The signed case falls through to a real division.
- Source: `cx = client_w / 2` (`window3d.p8:54`), where `client_w` is a `word`.
- **Implemented** (uncommitted change to `IRCodeGen.kt`): `emitSignedDivByPow2Shift`
  (`IRCodeGen.kt:1421-1444`) is unwrapped from its block comment and both call sites are
  active, guarded by `options.compTarget.cpu.is68k` so the 6502 stays disabled. The
  bias-corrected shift is `(x + ((x >> (W-1)) & (2^k - 1))) >> k`, which matches
  truncating division. `divideByConstInplace` loads the value, converts it and stores it
  back to the same address, so the in-place path is covered too. It also folds signed
  `long` division (`divs.l #$0100` in window3d), not just the eight `word` sites.
- Coverage is complete: the only other immediate-division path in `ExpressionGen.kt`
  needs a float-typed `PtNumber` as the right operand of an integer division, which
  type checking rules out, and the `DIVSR` + immediate-load peephole fold left no
  `divs` by a power of two in the generated IR of the four baseline programs.
- Measured on `master` (fractal-tree, gfxdemo, window3d, tictactoe; baseline is the
  same compiler built from git `HEAD`):

| Example | `.asm` | binary |
| --- | --- | --- |
| fractal-tree | +0.21% | +1.27% |
| gfxdemo | +0.50% | +1.95% |
| window3d | +0.62% | +2.81% |
| tictactoe | +0.35% | +1.57% |

  On `master` there is no register allocation, so each of the roughly eight IR
  instructions of the bias sequence becomes regfile moves: one site grows from
  `ext.l d0; divs.w #2,d0` (2 instructions) to about a dozen. The earlier
  "about five instructions" estimate does not hold on `master`, and a per-cycle win
  is not proven there. window3d binary growth exceeds the 2% size limit, so whether
  the change stays on `master` or is moved to the register allocation branch is not
  decided (see the F2 entry under Open questions). On the allocator path the sequence
  collapses to a few register instructions, which is where the payoff is expected.
- AST processing: `ExpressionSimplifier.kt:1017` only rewrites unsigned division by a
  power of two as an exact shift. The signed rule depends on the target, so it belongs in
  IR codegen.

### F3 - zero-extended register copy (IR codegen)

- Asm: `moveq #0,d5` then `move.b d4,d5` (404 matches).
- IR: `loadm.b` followed by `ext.b` (zero extension). Earlier trace:
  `circles.p8ir:2995-2996`.
- Source: `(1 as word)-radius` (`circles.p8:76` in the earlier version).
- Owner: IR codegen fuses `loadm.b` with the following `ext.b` into one zero-extending
  load (ranked item 2). This is what makes peephole item B1 worthwhile. The allocator
  part (placing the load result in the register that `ext.b` writes) is M3 in the
  peephole document.
- AST processing: the implicit widening is required. No change.

### F5 - sign-extension after a byte copy (IR codegen)

- Asm: `move.b d2,d3` then `ext.w d3` (`window3d.asm:3863-3865`, four times in
  `update_geometry`). 32 matches in total.
- IR: `loadi.b r284.b,[r283.p+54]` then `exts.b r285.w,r284.b`
  (`window3d.p8ir`, `update_geometry`).
- Source: `border_left = win.BorderLeft` (`window3d.p8:46`). `BorderLeft` is a byte
  field that is widened to `word`.
- Owner: IR codegen. The same fusion as F3 (ranked item 2), extended to the indexed
  `loadi.b` form. The allocator coalescing part is M4 in the peephole document.
- AST processing: the implicit conversion is correct. No change.

### F8 - byte zero-extension before divu.w (IR)

- Asm: `and.l #$ff,d2` before `divu.w`. 21 matches. On `master` the backend emits the
  same `and.l #$ff, d0` (`InstrArithmetic.kt:230`, `310-330`).
- IR: `loadm.b` followed by `mod.b` (`circles.p8ir:2935-2936`).
- Owner: the backend (`InstrArithmetic.kt:245-380`) inserts the extension itself. The
  IR cannot see it. The fix is to make the extension explicit in IR (ranked item 3).

## Tier C - IR codegen

| ID | Observation | Owner | Notes |
| --- | --- | --- | --- |
| C1 | Prologues clear bytes already zeroed by an earlier `clr.l`/`clr.w` in the same frame | IR codegen (`StackFrameLayout`) | Dedupe in the locals zeroing logic. The `clr.b` pairs in `circles` `drawDisc` are an example (see the traces below). Not re-measured in the re-scan. |

C2 (frame locals stored and reloaded across calls) is part of promotion, so it is M9 in
the peephole document. C3, C4 and B1b are backend, AsmGen or allocator items, so they are
there too (M7, M6 and M5).

## Earlier-stage findings

Tracing the patterns back through the IR and the source shows that several of the
items above start earlier in the compilation chain. Fixing them there is cheaper than a
peephole at the end, and removes more than one asm pattern at a time.

The traces below were recorded against the earlier `circles` example, before its
random placement change. Line numbers may have moved.

### Traces

| Asm (`circles`, `blitcop`, `wavc`) | IR (`.p8ir`) | Source | Origin |
| --- | --- | --- | --- |
| `clr.l -6(a5)`, then `clr.b -6`, `clr.b -4/-5/-3` | `storezm.l [-6]` then `storezm.b` on bytes already zeroed | `ubyte yy = 0`, `ubyte pendingRadius` | `StatementReorderer.kt:43-66` adds `x = 0` to every local. `StackFrameLayout.kt:634-664` zeroes the same bytes in the prologue. |
| `moveq #0,d5` / `move.b d4,d5` after `move.b 10(a5),d4` | `loadm.b` then `ext.b` | `(1 as word)-radius` | Implicit widening. The `loadm`/`ext` pair is not fused. |
| `and.l #$ff,d2` before `divu.w` | `loadm.b`, `mod.b` | `index % 10` | The zero-extension is emitted inside the backend (`InstrArithmetic.kt:245-380`), so IR cannot see it. |
| `bhi L36` / `bra L35` / `L36:` | `bgtr.w`, `jump`, label | loop in `wavc.p8` | `removeWeirdBranches` does not invert a conditional that jumps over a single `jump` chunk. |
| `addq.b #1,-5(a5)` / `move.b -5(a5),d2` / `cmpi.b #21` | `incm`, `loadm`, `cmpi` on `[frame:-5]` | `for index in 0 to 20` | Loop variable kept in frame memory. |

The zeroing duplication reproduces with a small program: the prologue zeroes three
bytes, then each declaration zeroes them again.

### Ranked earlier-stage opportunities

Counts from the earlier snapshots are approximate and not re-measured. The peephole
item IDs in the last column refer to `m68k-asm-peepholes.md`.

1. **Remove provably redundant zero stores** after `StackFrameLayout`. A forward
   "known zero" scan over the first chunk is safe for framed and static subroutines
   alike. Do not remove the `StatementReorderer` zero assignment: static subroutines
   need it on every call.
   Removes: C1.
2. **Fuse `loadm.b`/`loadm.w` and `loadi.b` with a following `ext`** into a zero- or
   sign-extending load. 240 adjacent `loadm` pairs in the earlier snapshots, plus the
   indexed `loadi.b` + `exts.b` form (F5). Saves one register copy per instance. Either a
   new load form or a fold in `collapseConversions`.
   Removes: peephole B1 (its main gain). The allocator side of the same copies is M3
   and M4 in the peephole document.
3. **Make byte div/mod zero-extension explicit in IR.** Currently the backend emits
   `and.l #$ff` itself. An explicit `ext.b` feeding a word operation would let item 2
   see it. Medium confidence: this also changes the 6502 IR, so it needs a check
   there.
   Removes: peephole A6 (fallback only).
4. **Invert a conditional branch over a single `jump` chunk.** One asm case (`wavc`).
   Cheap, low payoff.
   Removes: peephole A7.

Local promotion of scalar locals to vregs (formerly item 1 here) is M9 in the peephole
document, because it only pays off with the register allocator.

The backend-level items (small constants as `moveq`, shift counts above 8, library base
reloads) are also in the peephole document, as A5, M6 and M7.

Effect on the tiers, as the peephole IDs that each item would remove:

| Item | Removes |
| --- | --- |
| 1 (known-zero pass) | C1 |
| 2 (load/ext fusion) | B1 |
| 3 (explicit zero-extension) | A6 (fallback) |
| 4 (branch inversion) | A7 |

### Byte-truncation trap (fixed in the example)

`examples/amiga/circles.p8`, `uword x = 80 + (index % 10) * 159 / 2`, computed the
product in `ubyte`. With `index = 9` the intermediate result was 155 instead of 795.
The example now uses `((index % 10) as uword) * 159 / 2`. Whether the compiler should
warn about this pattern is still open (see open questions).

The same kind of trap exists in `examples/cx16/circles.p8` (lines 65-66) and
`benchmark-program/b_circles.p8` (lines 94-95): `dx*dx as uword` multiplies two `word`
values, so the product overflows once `|dx| > 181`. Those are word truncations, not
byte truncations, and are not changed yet.

### AST processing: what it can and cannot change

None of the families above is fixed by the AST. The AST keeps the source semantics,
and the asm patterns come from the IR, the allocator, the backend and AsmGen. The one
AST-related item is the byte-truncation check (open question 1), which is about
correctness. Zero-initialising locals in `StatementReorderer` is also AST-level, but it
is needed for static subroutines, so the duplicate zeroing (C1) is fixed in IR codegen
(`StackFrameLayout`), not in the AST.

## Work

1. **Hand-off.** Record each item here in `docs/source/todo.rst` or in the register
   allocation stage documents, as a task for its owner (IR codegen or AST processing).
   Allocator, AsmGen, backend and promotion items are in `m68k-asm-peepholes.md` under
   "Moved items", with the owner named there. Do not implement any of these as
   peepholes.
2. **Measure before changing.** For F1 (the store/load fold), measure the baseline with
   and without the change on `master`, and check the 2% size limit. Done for F2: the
   uncommitted change was measured against a `HEAD` baseline; window3d exceeds the 2%
   binary limit and the rest is below it (numbers in the F2 section).
3. **Verification.** Same as the peephole document: the `codeGenM68k` tests, a compiler
   install, and runs on `amiga500` (see the peephole document for the emulator notes).
   Because items 1-4 and F1/F2 change shared IR, also compile a 6502 or other non-m68k
   target before and after. Use `-compareir` to check that only the intended changes
   appear, and that no other output changes.

## Open questions

Answered:

- **Plan structure.** The plan is split into two documents. `m68k-asm-peepholes.md`
  holds the peephole rewrites and the allocator-dependent items ("Moved items"). This
  document holds the AST and IR codegen items that apply to `master`.
- **Signed division by a power of two for m68k (F2).** `emitSignedDivByPow2Shift` is
  enabled for the 68k CPU family only (`is68k`); the 6502 path stays disabled. It is
  implemented and measured, but on `master` the size result is growth, not a gain
  (window3d +2.81% binary, above the 2% limit), because the regfile path turns the
  shift sequence into many regfile moves. Whether the change stays on `master` or is
  gated on the register allocation branch is not decided yet.

Still open:

1. **Byte-truncation check.** Should an expression that computes a byte product and
   assigns it to a wider type be a compiler warning, an error, or left as documented
   behaviour? The check would go in the AST checker.
2. **Re-enable the store/load fold (F1).** Is `foldStoremLoadmToLoadr` still a loss on
   `master`, where the regfile path is the default? Measure with and without the fold on
   the baseline before deciding.
