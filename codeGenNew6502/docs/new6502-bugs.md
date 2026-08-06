New6502 Codegen Correctness Bugs
================================

This document lists known correctness bugs in the new6502 code generator
(codeGenNew6502). Each bug gives the location, a description, and, where
available, the evidence and a suggested fix.

Bugs marked CONFIRMED were reproduced by compiling a test program and
inspecting the generated assembly or the assembler errors. Bugs marked
SOURCE-AUDITED were identified by reading the code but not yet reproduced
end-to-end.

Background
----------

- Targets: cx16 uses the 65C02. c64, pet32 and c128 use the plain 6502 and
  may NOT use 65C02-only instructions (STZ, BRA, PHX/PHY, PLX/PLY, TRB/TSB,
  RMB/SMB, INC/DEC accumulator, JMP (abs,x), BBR/BBS, etc.).
- IR virtual registers live in a memory "register file" (`p8_regfile`, BSS),
  with variable-size slots. A LONG occupies ONE register number (4 bytes).
  The old CPU register pair convention (R14/R15 = two words) does NOT map to
  the new single register number.
- `AsmGen.kt` provides 65C02-aware helpers `emitStoreZero()`,
  `emitIncrementA()` and `emitDecrementA()` that fall back to plain 6502
  equivalents. All other emitted instructions must be hand-checked for
  65C02-only usage.
- Verification flags: `prog8c -target <t> -newcodegen -out . file.p8`.
  Compiling for pet32 is the fastest way to catch 65C02-only instructions,
  since the 64tass assembler will reject them.

Bug list
--------

1. LONG return value capture stores bytes into the WRONG register  [CONFIRMED]
   --------------------------------------------------------------------------
   Location: InstrControl.kt, `translateReturnValue()`, lines 840-848
   (the `IRDataType.LONG` case when `callingConventionSlot == null`).

   The callee returns a LONG in `cx16.r14/r15` (RETURNR, lines 166-175,
   is correct). The caller captures it with:

       lda  cx16.r14
       sta  regAddrLo(regNum)      ; byte 0   OK
       lda  cx16.r14+1
       sta  regAddrHi(regNum)      ; byte 1   OK
       lda  cx16.r15
       sta  regAddrLo(regNum + 1)  ; byte 2 -> WRONG REGISTER
       lda  cx16.r15+1
       sta  regAddrHi(regNum + 1)  ; byte 3 -> WRONG REGISTER

   In the new register model a LONG occupies a SINGLE register number
   (4 bytes). `regAddrLo/Hi(regNum + 1)` address the *next* register, so
   bytes 2-3 of the result land in a neighbouring register slot and the
   result's own bytes 2-3 are left stale.

   Evidence: test `t1_longret.p8` (function returning a long), generated
   assembly lines 411-418:

       lda  cx16.r14          -> sta  p8_regfile+0
       lda  cx16.r14+1        -> sta  p8_regfile+1
       lda  cx16.r15          -> sta  p8_regfile+4   ; r2's low word!
       lda  cx16.r15+1        -> sta  p8_regfile+5

   Fix: use `regAddrByte(regNum, 2)` / `regAddrByte(regNum, 3)` for the
   r15 bytes.

2. STOREZX WORD: high byte is zeroed at a garbage index  [CONFIRMED]
   ------------------------------------------------------------------
   Location: InstrLoadStore.kt, `zeroMemoryIndexed()`, WORD/POINTER case,
   lines 851-855.

       ldx  regAddrLo(reg)   ; scaled index in X
       emitStoreZero("$baseAddress,x")
       ldx  regAddrHi(reg)   ; BUG: reloads X with the register's HIGH byte
       emitStoreZero("${baseAddress}+1,x")

   The index register is a BYTE-sized scaled index; its high byte is
   uninitialised garbage. The second store zeroes `arr+1+<garbage>` instead
   of `arr+1+<index>`, corrupting an unrelated element (usually element 0).

   Evidence: test `t3b_storezx_nosplit.p8` (`uword[] @nosplit @shared arr`,
   runtime index), generated assembly:

       ; storezx.w r1,p8b_main.p8v_arr
       ldx  p8_regfile+0
       stz  p8b_main.p8v_arr,x
       ldx  p8_regfile+1      ; <-- uninitialised high byte of the index
       stz  p8b_main.p8v_arr+1,x

   Fix: keep the scaled index in X across both stores:
       ldx  regAddrLo(reg)
       emitStoreZero("$baseAddress,x")
       emitStoreZero("${baseAddress}+1,x")

   (Note: `@nosplit` is required to reach this path; on 6502 targets word
   arrays default to split-words, which uses two BYTE stores and is fine.)

3. STOREZX LONG: only the low byte is zeroed  [SOURCE-AUDITED]
   ------------------------------------------------------------
   Location: InstrLoadStore.kt, `zeroMemoryIndexed()`, LONG case,
   lines 856-859.

       ; STOREZX LONG not fully implemented
       emitStoreZero("$baseAddress,x")

   Only one byte is written for a 4-byte element. The array (or pointer
   target) is left with the upper 3 bytes untouched. Compare with the
   STOREZI path (`zeroIndexed()`, lines 832-838) which correctly clears all
   4 bytes.

   Fix: emit a 4-byte loop (like `zeroIndexed`) or 4 explicit
   `emitStoreZero()` calls with offsets 0..3.

4. ALIGN is silently ignored  [CONFIRMED]
   ---------------------------------------
   Location: InstrControl.kt, `Opcode.ALIGN`, lines 302-305.

       Opcode.ALIGN -> {
           val alignment = imm ?: 256
           emitLine("; ALIGN to $alignment bytes")
       }

   Only a comment is emitted; no `.align` directive. The old codegen emits
   `  .align  N` (codeGenCpu6502/AsmGen.kt, `PtAlign` handling).

   Evidence: test `t4_align.p8` (`%align 256`). Under `-newcodegen` the
   output contains only the comment `; ALIGN to 256 bytes` and no `.align`.
   The same source compiled with the old codegen emits `.align $0100`.

   Fix: emit `  .align  <alignment>` (matching the old codegen output).

5. Signed multiply/divide/modulo for BYTE and WORD is wrong  [SOURCE-AUDITED]
   --------------------------------------------------------------------------
   Location: InstrArithmetic.kt.

   - `mulSignedRegisters/Immediate/Memory` (lines 856-869) delegate to the
     unsigned `mulRegisters`/`mulImmediate`/`mulMemory` for ALL types. The
     comments claim prog8_math.multiply_longs handles signed longs, which is
     only true for LONG.
   - `divSignedRegisters`/`divSignedImmediate` (lines 1029-1081) use the
     unsigned `divmod_b_asm`/`divmod_w_asm` for BYTE/WORD. Only the LONG
     case delegates to the signed `prog8_math.div_longs`.
   - `modSignedImmediate` (lines 1144-1147) silently uses the unsigned
     `modImmediate`.
   - `modSignedRegisters` (lines 1140-1142) is an unimplemented
     `TODO("MODSR ... (signed)")` - a Kotlin crash. The IR does emit
     `Opcode.MODSR` (codeGenIntermediate AssignmentGen.kt line 221) for
     `x %= y` on signed types, so a signed register modulo crashes the
     compiler.

   Consequences: negative operands give wrong results for BYTE/WORD signed
   multiply, divide and modulo, and signed register modulo crashes the
   compiler. The file header (line 19) acknowledges the fallthrough.

   Fix: implement sign handling (negate operands, do unsigned op, fix sign
   of result) for BYTE/WORD, and implement `modSignedRegisters`.

6. JUMPI/CALLI use `jmp (abs)` which has the 6502 page-wrap bug  [SOURCE-AUDITED]
   ------------------------------------------------------------------------------
   Location: InstrControl.kt, `Opcode.JUMPI` line 43-46, `Opcode.CALLI`
   line 54-62.

   `jmp (addr)` on the original 6502 misbehaves when the address of the
   pointer ends in $FF: the high byte is fetched from the same page ($xx00)
   instead of the next page. The 65C02 fixes this. The new codegen's virtual
   registers are laid out contiguously in `p8_regfile`; a register whose
   slot happens to end at a page boundary would trigger this on c64/pet32/
   c128.

   This is a latent, low-probability bug (depends on the BSS placement of
   the register file), but it is worth fixing for correctness, e.g. by
   forcing the pointer to a page-safe address or using an indexed indirect
   jump (which is 65C02-only, so no) or loading the target into a known-safe
   location first.

7. Inline asmsub routines are emitted as regular subroutines  [SOURCE-AUDITED]
   ---------------------------------------------------------------------------
   Location: AsmGen.kt, documented in the file header, lines 18-27.

   `inline asmsub` blocks in Prog8 source must be inserted at the call site
   (no jsr, no rts). The IR does not preserve the `inline` flag, so the new
   codegen emits them as normal subroutines, which is wrong: they lack an
   rts and their A/X/Y return values are not captured correctly.

    Fix: add an INLINE attribute to ASMSUB in the IR format, or ensure the
    optimizer inlines these before IR generation.

Reproduction tests
------------------


The following test programs live under `/tmp/opencode/n6502test/` and were
used to confirm the bugs above:

- `t1_longret.p8`          - long function return value (bug 2)
- `t2_ftosl.p8`            - runtime float-to-long (bug 3)
- `t3b_storezx_nosplit.p8` - store-zero to word array with @nosplit (bug 4)
- `t4_align.p8`            - `%align 256` (bug 6)
- `t7_probe.p8`            - FTOSL result register dump + str_l printing (bug 1)

Compile with e.g.:

    prog8c -target cx16  -newcodegen -out . t1_longret.p8
    prog8c -target pet32 -newcodegen -out . t2_ftosl.p8
