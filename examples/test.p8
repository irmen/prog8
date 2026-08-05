%import textio
%zeropage basicsafe

; Tests for "NOT directly on memory" in the m68k codegen.
; See codeGenM68k/docs/m68k-optimizations.md item 1:
;
;   - INV on register-file destinations (invertRegister in InstrBitwise.kt)
;     emits a 3-instruction roundtrip:
;         move.x  p8_regfile+off, d0
;         not.x   d0
;         move.x  d0, p8_regfile+off
;   - In-place invert on plain variables (INVM, invertMemory) roundtrips
;     through d0 the same way.
;
; `not.b/w/l` supports a memory destination directly on all 68000-family
; cpus, so both patterns could collapse to a single `not.x mem`.
;
; The test covers byte, word and long sizes for both patterns, and prints
; the resulting and expected value in hex so correctness is easy to verify.
;
;   prog8c -target amiga500 -out /tmp/opencode examples/test.p8
;   rg '^\s*(inv|invm)\.' /tmp/opencode/test.p8ir
;   rg -n -B1 -A2 '^\s*not\.' /tmp/opencode/test.asm
;   prog8c -target amiga500 -emu examples/test.p8   (uses vamos emulator)

main {
    sub start() {
        test_inv()
        test_invm()
        test_zero_loads()
    }

    sub test_inv() {
        ubyte @shared b = $b5
        ubyte @shared r = ~b
        txt.print("inv byte:  ~$b5 = ")
        txt.print_ubhex(r, true)
        txt.print("  expected $4a")
        txt.nl()

        uword @shared w = $f00f
        uword @shared rw = ~w
        txt.print("inv word:  ~$f00f = ")
        txt.print_uwhex(rw, true)
        txt.print("  expected $0ff0")
        txt.nl()

        long @shared l = $ff00ff00
        long @shared rl = ~l
        txt.print("inv long:  ~$ff00ff00 = ")
        txt.print_ulhex(rl, true)
        txt.print("  expected $00ff00ff")
        txt.nl()
        txt.nl()
    }

    sub test_invm() {
        ; in-place invert on plain variables -> INVM memory variant
        ubyte @shared bm = $b5
        bm = ~bm
        txt.print("inv= byte: ~$b5 = ")
        txt.print_ubhex(bm, true)
        txt.print("  expected $4a")
        txt.nl()

        uword @shared wm = $f00f
        wm = ~wm
        txt.print("inv= word: ~$f00f = ")
        txt.print_uwhex(wm, true)
        txt.print("  expected $0ff0")
        txt.nl()

        long @shared lm = $ff00ff00
        lm = ~lm
        txt.print("inv= long: ~$ff00ff00 = ")
        txt.print_ulhex(lm, true)
        txt.print("  expected $00ff00ff")
        txt.nl()
    }

    sub test_zero_loads() {
        ; "Zero loads -> clr": loading immediate 0 into a slot should emit
        ; clr instead of move. See m68k-optimizations.md item 1.
        ;
        ;   - `for ch in "..."` string iteration emits LOAD byte 0 for the
        ;     index register (IRCodeGen.kt)
        ;   - `for i in 0..n` numeric range emits STOREIM word 0 for the
        ;     loop variable (IRCodeGen.kt)
        ;
        ; Both should become clr in the generated asm.

        ; string iteration -> LOAD byte 0
        uword @shared count = 0
        ubyte ch
        for ch in "test" {
            count++
        }
        txt.print("string iteration count = ")
        txt.print_uw(count)
        txt.print("  expected 4")
        txt.nl()

        ; numeric range starting at 0 -> STOREIM word 0
        uword @shared sum = 0
        uword i
        for i in 0 to 4 {
            sum += i
        }
        txt.print("range 0..4 sum = ")
        txt.print_uw(sum)
        txt.print("  expected 10")
        txt.nl()
    }
}
