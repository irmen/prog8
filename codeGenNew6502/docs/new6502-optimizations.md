New6502 Codegen Performance Optimizations
==========================================

The new6502 codegen has NO CPU register allocation. IR virtual registers live
in a memory "register file" (`p8_regfile`, in BSS), with variable-size slots
per register type. Every instruction that touches a register does so through
that memory slot using the A/X/Y registers as scratch. The file is sized
dynamically from `registersUsed()`, so only the actually-used registers cost
memory. Long (32-bit) operations in particular are expensive: each one is 4
separate load/op/store round trips through memory.

This document lists improvements ordered roughly by impact and ease, in the
same spirit as the m68k analysis (codeGenM68k/docs/m68k-optimizations.md).
Items that fix outright correctness bugs are flagged [BUG].

What is already good
---------------------

Some patterns are already tight and should be preserved:

- STOREZM / STOREIM zeroing uses `stz` on 65C02 (cx16) targets
- WORD add/sub with immediate is emitted as a single carry-propagating
  `clc/adc`/`sec/sbc` chain on the slot, no extra temp
- BYTE INC/DEC on 65C02 uses `ina`/`dea`
- WORD INC/DEC use the branch-to-skip carry propagation pattern
- LONG add/sub are unrolled (fast), not looped
- signed word comparisons use the SEC/SBC/EOR #$80 technique
- `lda slot; cmp #imm` for unsigned word compare/branch with the
  operand-swap trick for `>` and `<=` (no branch-over-jmp)
- variable-count shifts use a single X-counter loop (compact)
- constant-count shifts are unrolled (fast)
- CALLI is turned into CALL by the IR peephole when possible
- the regfile layout is packed by register type, sized by actual use
- address computation for indirect access uses a single ZP scratch word

Suggested order of implementation
-----------------------------------

1. A/X/Y register cache (biggest win, no real allocation)
2. Peephole optimizer expansion on the emitted assembly
3. Byte BGT/BLE via operand swap
4. `addImmediate`/`subImmediate` value==1 to `inc`/`dec` memory
5. 65C02-only instruction audit (FTOSL uses `stz`/`bra` on all targets) [BUG]
6. TSB/TRB for BITSET/BITCLR on 65C02
7. ZP placement of hot virtual registers
8. Float FAC1 caching and DEC via FADD -1.0
9. Inline asmsub handling [BUG]
10. STOREZX LONG zeroing incomplete [BUG]
11. JMP (abs) page-wrap on plain 6502 [BUG]
12. Unroll remaining multi-byte loops for speed


1. A/X/Y register cache (biggest win, still no full allocation)
----------------------------------------------------------------

The scratch usage is extremely regular. BYTE ops funnel through A:
`lda slot; <op>; sta slot`. After a `sta slot`, A still holds that value.
The next IR instruction frequently reads that same slot again (e.g.
`a = b + c` followed by `d = a + e`). Tracking "A currently holds virtual
register rX" lets the codegen skip the redundant `lda slot`.

Track the same for the X register (used as an index / second data holder in
`storeX`/`indirectStore`/STOREZX paths) if the A cache proves effective.

Invalidation rules:
- any instruction that writes a register-file slot (the slot value in A/X
  becomes stale, and A itself is clobbered by most ops anyway)
- any ALU instruction (clobbers A)
- any branch target / basic-block boundary / chunk boundary
- any CALL / JUMP / PUSH / POP (callee-clobbers A/X/Y)
- after `lda #imm`, the cache holds an immediate, not a register slot
- after `lda` from a non-register memory operand, cache is empty

The biggest wins appear in long chains of `LOADR` + ALU + `STORER`, and in
the post-`sta` reload pattern described above.

Note: unlike m68k, flag-setting matters here. `cmp` sets flags for the
branch that follows, so a cached load must never be skipped if the following
instruction consumes the flags from the *load* itself (plain `lda` does not
set flags, so this is only a concern around `ldx`/`ldy` used as data).
Only skip pure loads, never reorder.

2. Peephole optimizer expansion
--------------------------------

The current `optimization/PeepholeOptimizer.kt` only rewrites CALLI to CALL
(42 lines). The old 6502 codegen has a rich assembly post-pass
(codeGenCpu6502/AsmOptimizer.kt) with proven patterns that are still missing
here:

- optimizeSameAssignments (redundant `sta slot; lda slot`)
- optimizeStoreLoadSame
- optimizeIncDec
- optimizeJsrRtsAndOtherCombinations (jsr + rts -> jmp, branch-over-jmp)
- optimizeUselessPushPopStack
- optimizeTSBtoRegularOr
- optimizeUnneededTempvarInAdd

Because AsmGen emits text directly to a StringBuilder, the natural fit is a
post-pass over the emitted lines (same design as the old AsmOptimizer), or
folding these patterns into the IR peephole that already runs.

3. Byte BGT/BLE via operand swap
---------------------------------

Unsigned byte `a > imm` currently emits:
```
lda  slot
cmp  #imm
bcc  skip
beq  skip
jmp  label
skip:
```
(6 instructions, a label, and a branch-over-jmp). The WORD path already
swaps operands to `lda #imm; cmp slot; bcc label`. The same swap works for
bytes:
```
lda  #imm
cmp  slot
bcc  label
```
3 instructions, no label. Same for `a <= imm` (`bcs` instead of `bcc`).
This also removes the `jmp` that breaks the short-branch range.

4. `addImmediate`/`subImmediate` value==1 -> `inc`/`dec` memory
----------------------------------------------------------------

On plain 6502 (c64/pet32/c128), BYTE ADDIM/SUBIM with value 1 emits:
```
lda  slot
clc
adc  #1
sta  slot
```
(4 instructions) even though the target is a memory slot. `inc slot` /
`dec slot` do the whole thing in one instruction. The 65C02 path already
handles value==1 with `ina`/`dea`; extend the plain-6502 path to use the
memory `inc`/`dec` form (only for BYTE type, and only when the value is
exactly 1, never for signed -1 which would need a different form).

5. 65C02-only instruction audit [BUG]
---------------------------------------

`translateFloatToSignedLong` (InstrControl.kt, FTOSL) emits `stz` and `bra`
unconditionally. Both are 65C02-only instructions. A program using floats on
a c64/pet32/c128 target (plain 6502) will therefore fail to assemble or
produce garbage. It must use `lda #0; sta` and `jmp` on non-65C02 targets.

Audit the whole codegen for other ungated 65C02 instructions (`stz`, `bra`,
`tsb`, `trb`, `ina`, `dea`, `phx`, `phy`, `plx`, `ply`, `stx zp,y`, etc.)
and gate them on `is65C02()`.

6. TSB/TRB for BITSET/BITCLR on 65C02
--------------------------------------

BITSET/BITCLR/BITTOG currently emit:
```
lda  slot
ora/and/eor  #mask
sta  slot
```
(3 instructions) on every target. On 65C02, TSB/TRB operate directly on
memory (zeropage and absolute), collapsing BITSET and BITCLR to a single
instruction. BITTOG has no TSB/TRB equivalent, keep it as-is. Apply only
when `is65C02()`, keeping the current form on plain 6502.

7. ZP placement of hot virtual registers
-----------------------------------------

Every register-file access uses absolute addressing (3 bytes). Moving the
hottest virtual registers into zeropage makes those accesses 2-byte and
faster. The full regfile can never live in ZP (ZP is only 256 bytes), but:

- reuse the existing ZeropageAllocator (it already scores variables by usage
  frequency and type) and extend the scoring to virtual registers, or
- overlay a small set of "hot" virtual registers on dedicated ZP slots and
  have `regAddrLo()`/`regAddrHi()`/`regAddr()` return those ZP labels.

Combined with the IR-level RegisterPacker (currently disabled because it
"doesn't work well", commit b0cf649c2), this is the closest step to real
register allocation and the biggest structural win available.

8. Float FAC1 caching and DEC via FADD -1.0
---------------------------------------------

Every float op loads the operand from the fp regfile into FAC1 and stores
the result back. Add an FAC1 cache analogous to the A cache: track "FAC1
currently holds fp register frX" and skip the redundant MOVFM.

DEC/DECM for floats is especially wasteful: it does MOVFM + pushFAC1 +
MOVFM(1.0) + popFAC + FSUBT (because FSUB computes memory - FAC1). Since FADD
computes FAC1 + memory, DEC can simply FADD the constant -1.0:
```
jsr  floats.MOVFM
lda  #<-1.0 const
ldy  #>-1.0 const
jsr  floats.FADD
jsr  floats.MOVMF
```
dropping the push/pop and the FSUBT, and removing the register-clobbering
stack round trip.

9. Inline asmsub handling [BUG]
---------------------------------

The header of AsmGen.kt documents that `inline asmsub` bodies are emitted as
regular subroutines, which is wrong: inline asmsubs must be inserted directly
at the call site (no jsr, no .proc/.pend, no rts). Currently they are emitted
as standalone routines lacking an rts, so calling them returns garbage.
The IR does not preserve the `inline` flag, so either add an INLINE attribute
to the ASMSUB IR instruction, or ensure the optimizer inlines them before IR
generation.

10. STOREZX LONG zeroing incomplete [BUG]
-------------------------------------------

`zeroMemoryIndexed` (InstrLoadStore.kt) handles BYTE and WORD, but the LONG
case emits only the low byte with the comment "STOREZX LONG not fully
implemented". Zeroing a long array element via STOREZX therefore leaves the
upper 3 bytes untouched. The WORD pattern (reload X with the high byte and
`sta base+1,x`) extends naturally to LONG by repeating for each byte.

11. JMP (abs) page-wrap on plain 6502 [BUG]
---------------------------------------------

JUMPI and CALLI emit `jmp (p8_regfile+N)`. On the original 6502, `JMP (abs)`
reads the high byte from the same page (the address wraps at $xxFF); 65C02
fixed this. If a dispatch/indirect target stored in a register slot has a
low byte of $FF, c64/pet32/c128 programs will jump to a wrong address.
Mitigations: guarantee the regfile never holds a vector with low byte $FF
(not under the compiler's control in general), or emit the classic
workaround (push low, load high, push high, rts) on non-65C02 targets.

12. Unroll remaining multi-byte loops for speed
-------------------------------------------------

LONG AND/OR/XOR use a `ldy #3` byte loop; LONG add/sub are unrolled. The
loop is smaller, the unroll is faster. Pick one policy (unroll everything
for speed, loop everything for size) rather than the current mix, or gate on
an optimization-level switch. Also `copyRegister` for LONG could be unrolled.

Future register allocation
----------------------------

The real fix for all of this is a proper register allocator that assigns
frequently-used virtual registers to physical resources: A, X, Y, the
s0-s5 calling-convention slots, and a set of ZP scratch words. The IR
RegisterPacker (register-packing.md, graph-coloring packer, currently
disabled) reduces the *number* of virtual registers and could be combined
with ZP slot assignment so that the remaining hot registers fit in ZP. The
A/X/Y cache in item 1 is a cheap stepping stone toward that.
