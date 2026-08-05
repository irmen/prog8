%import textio
%zeropage basicsafe

; Tests for "Compare-and-branch immediates: skip the load" in the m68k codegen.
; See codeGenM68k/docs/m68k-optimizations.md item 1:
;
;   - cmpBranchUnsignedImm and cmpBranchSignedImm (InstrBranch.kt) currently emit:
;         move.x  p8_regfile+off, d0
;         cmpi.x  #imm, d0      (or tst.x d0 for imm == 0)
;         bxx     label
;   - cmpi.x #imm, <ea> accepts a memory operand directly, so the redundant
;     register-file load can be skipped:
;         cmpi.x  #imm, p8_regfile+off
;         bxx     label
;     and for imm == 0:
;         tst.x   p8_regfile+off
;         bxx     label
;
; `if (x > imm)` style conditions compile to the BGT/BGE/BLT/BLE (unsigned) and
; BGTS/BGES/BLTS/BLES (signed) branch opcodes with an immediate operand (the IR
; optimizer rewrites `>` / `<=` into `>=` / `<` with an adjusted immediate, and
; unsigned zero-compares into BSTEQ/BSTNE, so BGE/BLT and the signed variants
; are what actually reach the m68k codegen).
;
; Convention: every case prints PASS when the condition evaluates to its
; expected truth. Cases that expect the condition to be FALSE use `not` (which
; merely swaps the branch labels, still using the same branch opcode).
;
;   prog8c -target amiga500 -out /tmp/opencode examples/test.p8
;   rg '^\s*(bge|blt|bgts|bges|blts|bles)\.' /tmp/opencode/test.p8ir
;   rg -n -B2 '^\s*(bhs|blo|bls|bge|blt|bgt|ble) ' /tmp/opencode/test.asm
;   prog8c -target amiga500 -emu examples/test.p8   (uses vamos emulator)

main {
    ubyte @shared pass_count = 0
    ubyte @shared fail_count = 0

    sub start() {
        test_unsigned()
        test_signed()
        test_zero()
        txt.nl()
        txt.print("summary: pass=")
        txt.print_ub(pass_count)
        txt.print(" fail=")
        txt.print_ub(fail_count)
        txt.nl()
    }

    sub check(ubyte ok) {
        if ok != 0 {
            pass_count++
            txt.print("PASS\n")
        } else {
            fail_count++
            txt.print("FAIL\n")
        }
    }

    sub test_unsigned() {
        txt.print("--- unsigned ---\n")

        ubyte @shared b_hi = 100
        ubyte @shared b_lo = 20
        txt.print("ub 100 > 50: ")
        if b_hi > 50 { check(1) } else { check(0) }
        txt.print("ub 20  > 50: ")
        if not (b_lo > 50) { check(1) } else { check(0) }
        txt.print("ub 100 >= 50: ")
        if b_hi >= 50 { check(1) } else { check(0) }
        txt.print("ub 20  >= 50: ")
        if not (b_lo >= 50) { check(1) } else { check(0) }
        txt.print("ub 100 < 50: ")
        if not (b_hi < 50) { check(1) } else { check(0) }
        txt.print("ub 20  < 50: ")
        if b_lo < 50 { check(1) } else { check(0) }
        txt.print("ub 100 <= 50: ")
        if not (b_hi <= 50) { check(1) } else { check(0) }
        txt.print("ub 20  <= 50: ")
        if b_lo <= 50 { check(1) } else { check(0) }

        uword @shared w_hi = 60000
        uword @shared w_lo = 30000
        txt.print("uw 60000 > 40000: ")
        if w_hi > 40000 { check(1) } else { check(0) }
        txt.print("uw 30000 > 40000: ")
        if not (w_lo > 40000) { check(1) } else { check(0) }
        txt.print("uw 60000 >= 40000: ")
        if w_hi >= 40000 { check(1) } else { check(0) }
        txt.print("uw 30000 >= 40000: ")
        if not (w_lo >= 40000) { check(1) } else { check(0) }
        txt.print("uw 60000 < 40000: ")
        if not (w_hi < 40000) { check(1) } else { check(0) }
        txt.print("uw 30000 < 40000: ")
        if w_lo < 40000 { check(1) } else { check(0) }
        txt.print("uw 60000 <= 40000: ")
        if not (w_hi <= 40000) { check(1) } else { check(0) }
        txt.print("uw 30000 <= 40000: ")
        if w_lo <= 40000 { check(1) } else { check(0) }

        long @shared l_hi = 10000000
        long @shared l_lo = 1000000
        txt.print("ul 10000000 > 5000000: ")
        if l_hi > 5000000 { check(1) } else { check(0) }
        txt.print("ul 1000000 > 5000000: ")
        if not (l_lo > 5000000) { check(1) } else { check(0) }
        txt.print("ul 10000000 >= 5000000: ")
        if l_hi >= 5000000 { check(1) } else { check(0) }
        txt.print("ul 1000000 >= 5000000: ")
        if not (l_lo >= 5000000) { check(1) } else { check(0) }
        txt.print("ul 10000000 < 5000000: ")
        if not (l_hi < 5000000) { check(1) } else { check(0) }
        txt.print("ul 1000000 < 5000000: ")
        if l_lo < 5000000 { check(1) } else { check(0) }
        txt.print("ul 10000000 <= 5000000: ")
        if not (l_hi <= 5000000) { check(1) } else { check(0) }
        txt.print("ul 1000000 <= 5000000: ")
        if l_lo <= 5000000 { check(1) } else { check(0) }
    }

    sub test_signed() {
        txt.print("--- signed ---\n")

        byte @shared b_hi = 100
        byte @shared b_lo = -50
        txt.print("sb 100 > -20: ")
        if b_hi > -20 { check(1) } else { check(0) }
        txt.print("sb -50 > -20: ")
        if not (b_lo > -20) { check(1) } else { check(0) }
        txt.print("sb 100 >= -20: ")
        if b_hi >= -20 { check(1) } else { check(0) }
        txt.print("sb -50 >= -20: ")
        if not (b_lo >= -20) { check(1) } else { check(0) }
        txt.print("sb 100 < -20: ")
        if not (b_hi < -20) { check(1) } else { check(0) }
        txt.print("sb -50 < -20: ")
        if b_lo < -20 { check(1) } else { check(0) }
        txt.print("sb 100 <= -20: ")
        if not (b_hi <= -20) { check(1) } else { check(0) }
        txt.print("sb -50 <= -20: ")
        if b_lo <= -20 { check(1) } else { check(0) }

        word @shared w_hi = 20000
        word @shared w_lo = -20000
        txt.print("sw 20000 > -5000: ")
        if w_hi > -5000 { check(1) } else { check(0) }
        txt.print("sw -20000 > -5000: ")
        if not (w_lo > -5000) { check(1) } else { check(0) }
        txt.print("sw 20000 >= -5000: ")
        if w_hi >= -5000 { check(1) } else { check(0) }
        txt.print("sw -20000 >= -5000: ")
        if not (w_lo >= -5000) { check(1) } else { check(0) }
        txt.print("sw 20000 < -5000: ")
        if not (w_hi < -5000) { check(1) } else { check(0) }
        txt.print("sw -20000 < -5000: ")
        if w_lo < -5000 { check(1) } else { check(0) }
        txt.print("sw 20000 <= -5000: ")
        if not (w_hi <= -5000) { check(1) } else { check(0) }
        txt.print("sw -20000 <= -5000: ")
        if w_lo <= -5000 { check(1) } else { check(0) }

        long @shared l_hi = 10000000
        long @shared l_lo = -10000000
        txt.print("sl 10000000 > -5000000: ")
        if l_hi > -5000000 { check(1) } else { check(0) }
        txt.print("sl -10000000 > -5000000: ")
        if not (l_lo > -5000000) { check(1) } else { check(0) }
        txt.print("sl 10000000 >= -5000000: ")
        if l_hi >= -5000000 { check(1) } else { check(0) }
        txt.print("sl -10000000 >= -5000000: ")
        if not (l_lo >= -5000000) { check(1) } else { check(0) }
        txt.print("sl 10000000 < -5000000: ")
        if not (l_hi < -5000000) { check(1) } else { check(0) }
        txt.print("sl -10000000 < -5000000: ")
        if l_lo < -5000000 { check(1) } else { check(0) }
        txt.print("sl 10000000 <= -5000000: ")
        if not (l_hi <= -5000000) { check(1) } else { check(0) }
        txt.print("sl -10000000 <= -5000000: ")
        if l_lo <= -5000000 { check(1) } else { check(0) }
    }

    sub test_zero() {
        txt.print("--- zero immediate (tst path) ---\n")

        ubyte @shared ub = 7
        txt.print("ub 7 > 0: ")
        if ub > 0 { check(1) } else { check(0) }
        txt.print("ub 7 >= 0: ")
        if ub >= 0 { check(1) } else { check(0) }
        txt.print("ub 7 < 0: ")
        if not (ub < 0) { check(1) } else { check(0) }
        txt.print("ub 7 <= 0: ")
        if not (ub <= 0) { check(1) } else { check(0) }

        uword @shared uw = 60000
        txt.print("uw 60000 > 0: ")
        if uw > 0 { check(1) } else { check(0) }
        txt.print("uw 60000 >= 0: ")
        if uw >= 0 { check(1) } else { check(0) }
        txt.print("uw 60000 < 0: ")
        if not (uw < 0) { check(1) } else { check(0) }
        txt.print("uw 60000 <= 0: ")
        if not (uw <= 0) { check(1) } else { check(0) }

        long @shared ul = 70000000
        txt.print("ul 70000000 > 0: ")
        if ul > 0 { check(1) } else { check(0) }
        txt.print("ul 70000000 >= 0: ")
        if ul >= 0 { check(1) } else { check(0) }
        txt.print("ul 70000000 < 0: ")
        if not (ul < 0) { check(1) } else { check(0) }
        txt.print("ul 70000000 <= 0: ")
        if not (ul <= 0) { check(1) } else { check(0) }

        byte @shared sb = -5
        txt.print("sb -5 > 0: ")
        if not (sb > 0) { check(1) } else { check(0) }
        txt.print("sb -5 >= 0: ")
        if not (sb >= 0) { check(1) } else { check(0) }
        txt.print("sb -5 < 0: ")
        if sb < 0 { check(1) } else { check(0) }
        txt.print("sb -5 <= 0: ")
        if sb <= 0 { check(1) } else { check(0) }

        word @shared sw = -30000
        txt.print("sw -30000 > 0: ")
        if not (sw > 0) { check(1) } else { check(0) }
        txt.print("sw -30000 >= 0: ")
        if not (sw >= 0) { check(1) } else { check(0) }
        txt.print("sw -30000 < 0: ")
        if sw < 0 { check(1) } else { check(0) }
        txt.print("sw -30000 <= 0: ")
        if sw <= 0 { check(1) } else { check(0) }

        long @shared sl = -50000000
        txt.print("sl -50000000 > 0: ")
        if not (sl > 0) { check(1) } else { check(0) }
        txt.print("sl -50000000 >= 0: ")
        if not (sl >= 0) { check(1) } else { check(0) }
        txt.print("sl -50000000 < 0: ")
        if sl < 0 { check(1) } else { check(0) }
        txt.print("sl -50000000 <= 0: ")
        if sl <= 0 { check(1) } else { check(0) }
    }
}
