%import textio
%zeropage basicsafe

; Tests for "immediate logical ops" in the m68k codegen.
; See codeGenM68k/docs/m68k-optimizations.md item 1:
;
;   - AND/OR/XOR with an immediate constant on register-file destinations
;     (andImmediate/orImmediate/xorImmediate in InstrBitwise.kt) currently
;     emit a 3-instruction roundtrip: move.x mem,d0 ; op.x #imm,d0 ; move.x d0,mem
;   - In-place AND/OR/XOR on plain variables (ANDM/ORM/XORM, the
;     andMemory/orMemory/xorMemory variants) roundtrip through d0 as well.
;
; Both patterns could collapse to a single andi/ori/eori.x #imm, mem
; (.b/.w work on all m68k cpus, .l on a memory operand needs 68020+).
;
; The test covers byte, word and long sizes for all three opcodes, and prints
; the resulting and expected value in hex so correctness is easy to verify.
;
;   prog8c -target qemu68k -out /tmp/opencode examples/test.p8
;   rg -E '^\s*and\b|^\s*or\b|^\s*eor\b' /tmp/opencode/test.p8ir
;   rg -E '^\s*(andm|orm|xorm)\.' /tmp/opencode/test.p8ir
;   rg -E 'andi|ori|eori|^\s*and\.|^\s*or\.|^\s*eor\.' /tmp/opencode/test.asm
;   prog8c -target qemu68k -emu examples/test.p8

main {
    sub start() {
        test_and()
        test_or()
        test_xor()
        test_mem_variants()
    }

    sub test_and() {
        ubyte @shared b = $b5
        ubyte @shared r = b & $3c
        txt.print("and byte:  $b5 & $3c = ")
        txt.print_ubhex(r, true)
        txt.print("  expected $34")
        txt.nl()

        uword @shared w = $f0f0
        uword @shared rw = w & $0ff0
        txt.print("and word:  $f0f0 & $0ff0 = ")
        txt.print_uwhex(rw, true)
        txt.print("  expected $00f0")
        txt.nl()

        long @shared l = $ff00ff00
        long @shared rl = l & $0ff00ff0
        txt.print("and long:  $ff00ff00 & $0ff00ff0 = ")
        txt.print_ulhex(rl, true)
        txt.print("  expected $0f000f00")
        txt.nl()
        txt.nl()
    }

    sub test_or() {
        ubyte @shared b = $a0
        ubyte @shared r = b | $0f
        txt.print("or byte:   $a0 | $0f = ")
        txt.print_ubhex(r, true)
        txt.print("  expected $af")
        txt.nl()

        uword @shared w = $f00f
        uword @shared rw = w | $0ff0
        txt.print("or word:   $f00f | $0ff0 = ")
        txt.print_uwhex(rw, true)
        txt.print("  expected $ffff")
        txt.nl()

        long @shared l = $f0000000
        long @shared rl = l | $0f0f0f0f
        txt.print("or long:   $f0000000 | $0f0f0f0f = ")
        txt.print_ulhex(rl, true)
        txt.print("  expected $ff0f0f0f")
        txt.nl()
        txt.nl()
    }

    sub test_xor() {
        ubyte @shared b = $ff
        ubyte @shared r = b ^ $0f
        txt.print("xor byte:  $ff ^ $0f = ")
        txt.print_ubhex(r, true)
        txt.print("  expected $f0")
        txt.nl()

        uword @shared w = $f00f
        uword @shared rw = w ^ $0ff0
        txt.print("xor word:  $f00f ^ $0ff0 = ")
        txt.print_uwhex(rw, true)
        txt.print("  expected $ffff")
        txt.nl()

        long @shared l = $ffffffff
        long @shared rl = l ^ $0f0f0f0f
        txt.print("xor long:  $ffffffff ^ $0f0f0f0f = ")
        txt.print_ulhex(rl, true)
        txt.print("  expected $f0f0f0f0")
        txt.nl()
        txt.nl()
    }

    sub test_mem_variants() {
        ; in-place ops on plain variables -> ANDM/ORM/XORM memory variants
        ubyte @shared bm = $b5
        bm &= $3c
        txt.print("and= byte: $b5 &= $3c = ")
        txt.print_ubhex(bm, true)
        txt.print("  expected $34")
        txt.nl()

        uword @shared wm = $f0f0
        wm &= $0ff0
        txt.print("and= word: $f0f0 &= $0ff0 = ")
        txt.print_uwhex(wm, true)
        txt.print("  expected $00f0")
        txt.nl()

        long @shared lm = $ff00ff00
        lm &= $0ff00ff0
        txt.print("and= long: $ff00ff00 &= $0ff00ff0 = ")
        txt.print_ulhex(lm, true)
        txt.print("  expected $0f000f00")
        txt.nl()

        ubyte @shared bo = $a0
        bo |= $0f
        txt.print("or= byte:  $a0 |= $0f = ")
        txt.print_ubhex(bo, true)
        txt.print("  expected $af")
        txt.nl()

        uword @shared wo = $f00f
        wo |= $0ff0
        txt.print("or= word:  $f00f |= $0ff0 = ")
        txt.print_uwhex(wo, true)
        txt.print("  expected $ffff")
        txt.nl()

        long @shared lo = $f0000000
        lo |= $0f0f0f0f
        txt.print("or= long:  $f0000000 |= $0f0f0f0f = ")
        txt.print_ulhex(lo, true)
        txt.print("  expected $ff0f0f0f")
        txt.nl()

        ubyte @shared bx = $ff
        bx ^= $0f
        txt.print("xor= byte: $ff ^= $0f = ")
        txt.print_ubhex(bx, true)
        txt.print("  expected $f0")
        txt.nl()

        uword @shared wx = $f00f
        wx ^= $0ff0
        txt.print("xor= word: $f00f ^= $0ff0 = ")
        txt.print_uwhex(wx, true)
        txt.print("  expected $ffff")
        txt.nl()

        long @shared lx = $ffffffff
        lx ^= $0f0f0f0f
        txt.print("xor= long: $ffffffff ^= $0f0f0f0f = ")
        txt.print_ulhex(lx, true)
        txt.print("  expected $f0f0f0f0")
        txt.nl()
    }
}
