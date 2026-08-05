%import textio
%zeropage basicsafe

; Tests for "Bit ops directly on memory" in the m68k codegen.
; See codeGenM68k/docs/m68k-optimizations.md item 1:
;
;   - bitTest/bitSet/bitClear/bitToggle (InstrBitwise.kt) round-trip through d0:
;         move.x  p8_regfile+off, d0
;         btst.l  #bit, d0
;         move.x  d0, p8_regfile+off        (bitTest has no write-back)
;   - btst/bset/bclr/bchg with an immediate bit number accept a memory operand
;     (applied to a byte at that address), so the round-trip can collapse to a
;     single `bset #bit, mem` etc. for byte slots.
;
; The IR peephole optimizer converts these Prog8 patterns into the bit ops:
;   - `(x & power2) != 0` / `== 0`  -> BITTST (feeds a BSTEQ/BSTNE branch)
;   - `x | power2`                  -> BITSET
;   - `x & ~power2`                 -> BITCLR
;   - `x xor power2`                -> BITTOG
;
; NOTE: a plain assignment `x = x | power2` is rewritten to `x |= power2` by
; the optimizer and becomes an ORM/ANDM/XORM memory op, which does NOT go
; through the register bit-op peephole. So the tests here use `(x + 1) | pow2`
; etc.: the left operand lands in a virtual register (load + inc), and only
; then the bit op is applied, which triggers the BITSET/BITCLR/BITTOG/BITTST
; IR opcodes.
;
; The test covers byte, word and long sizes and all four ops, printing the
; resulting/expected values in hex so correctness is easy to verify.
;
;   prog8c -target amiga500 -out /tmp/opencode examples/test.p8
;   rg '^\s*(bittst|bitset|bitclr|bittog)\.' /tmp/opencode/test.p8ir
;   rg -n -B1 -A1 '^\s*(btst|bset|bclr|bchg)\.' /tmp/opencode/test.asm
;   prog8c -target qemu68k -emu examples/test.p8   (QEMU, vamos not installed)

main {
    ubyte @shared pass_count = 0
    ubyte @shared fail_count = 0

    sub start() {
        test_byte()
        test_word()
        test_long()
        txt.nl()
        txt.print("summary: pass=")
        txt.print_ub(pass_count)
        txt.print(" fail=")
        txt.print_ub(fail_count)
        txt.nl()
    }

    sub check(bool ok) {
        if ok {
            pass_count++
            txt.print("PASS\n")
        } else {
            fail_count++
            txt.print("FAIL\n")
        }
    }

    sub test_byte() {
        txt.print("--- byte ---\n")

        ; BITSET: (x+1) | 1   (set bit 0); $55+1=$56, $56|1 = $57
        ubyte @shared base = $55
        ubyte @shared bs = 0
        bs = (base + 1) | 1
        txt.print("ub ($55+1) | 1 = ")
        txt.print_ubhex(bs, true)
        txt.print("  expected $57")
        txt.nl()
        check(bs == $57)

        ; BITCLR: (x+1) & ~2   (clear bit 1); $56 & $fd = $54
        ubyte @shared bc = 0
        bc = (base + 1) & ~2
        txt.print("ub ($55+1) & ~2 = ")
        txt.print_ubhex(bc, true)
        txt.print("  expected $54")
        txt.nl()
        check(bc == $54)

        ; BITTOG: (x+1) ^ 8   (toggle bit 3); $56 ^ 8 = $5e
        ubyte @shared bt = 0
        bt = (base + 1) ^ 8
        txt.print("ub ($55+1) ^ 8 = ")
        txt.print_ubhex(bt, true)
        txt.print("  expected $5e")
        txt.nl()
        check(bt == $5e)

        ; BITTST: x & 1 != 0  (bit 0 set in $55) and x & 2 == 0 (bit 1 clear)
        txt.print("ub $55 & 1 != 0: ")
        if (base & 1) != 0 { check(true) } else { check(false) }
        txt.print("ub $55 & 2 == 0: ")
        if (base & 2) == 0 { check(true) } else { check(false) }
    }

    sub test_word() {
        txt.print("--- word ---\n")

        ; BITSET: (x+1) | $100   (set bit 8); $1234+1=$1235, $1235|$100 = $1335
        uword @shared base = $1234
        uword @shared ws = 0
        ws = (base + 1) | $100
        txt.print("uw ($1234+1) | $100 = ")
        txt.print_uwhex(ws, true)
        txt.print("  expected $1335")
        txt.nl()
        check(ws == $1335)

        ; BITCLR: (x+1) & ~$1000   (clear bit 12); $1235 & $efff = $0235
        uword @shared wc = 0
        wc = (base + 1) & ~$1000
        txt.print("uw ($1234+1) & ~$1000 = ")
        txt.print_uwhex(wc, true)
        txt.print("  expected $0235")
        txt.nl()
        check(wc == $0235)

        ; BITTOG: (x+1) ^ 2   (toggle bit 1); $1235 ^ 2 = $1237
        uword @shared wt = 0
        wt = (base + 1) ^ 2
        txt.print("uw ($1234+1) ^ 2 = ")
        txt.print_uwhex(wt, true)
        txt.print("  expected $1237")
        txt.nl()
        check(wt == $1237)

        ; BITTST: x & $10 != 0  (bit 4 set in $1234) and x & 8 == 0 (bit 3 clear)
        txt.print("uw $1234 & $10 != 0: ")
        if (base & $10) != 0 { check(true) } else { check(false) }
        txt.print("uw $1234 & 8 == 0: ")
        if (base & 8) == 0 { check(true) } else { check(false) }
    }

    sub test_long() {
        txt.print("--- long ---\n")

        ; BITSET: (x+1) | $40000000   (set bit 30); $12345678+1=$12345679
        ; $12345679 | $40000000 = $52345679
        long @shared base = $12345678
        long @shared ls = 0
        ls = (base + 1) | $40000000
        txt.print("ul ($12345678+1) | $40000000 = ")
        txt.print_ulhex(ls, true)
        txt.print("  expected $52345679")
        txt.nl()
        check(ls == $52345679)

        ; BITCLR: (x+1) & ~$10000000   (clear bit 28); $12345679 & $efffffff = $02345679
        long @shared lc = 0
        lc = (base + 1) & ~$10000000
        txt.print("ul ($12345678+1) & ~$10000000 = ")
        txt.print_ulhex(lc, true)
        txt.print("  expected $02345679")
        txt.nl()
        check(lc == $02345679)

        ; BITTOG: (x+1) ^ 8   (toggle bit 3); $12345679 ^ 8 = $12345671
        long @shared lt = 0
        lt = (base + 1) ^ 8
        txt.print("ul ($12345678+1) ^ 8 = ")
        txt.print_ulhex(lt, true)
        txt.print("  expected $12345671")
        txt.nl()
        check(lt == $12345671)

        ; BITTST: x & $10000000 != 0  (bit 28 set) and x & $08000000 == 0 (bit 27 clear)
        txt.print("ul $12345678 & $10000000 != 0: ")
        if (base & $10000000) != 0 { check(true) } else { check(false) }
        txt.print("ul $12345678 & $08000000 == 0: ")
        if (base & $08000000) == 0 { check(true) } else { check(false) }
    }
}
