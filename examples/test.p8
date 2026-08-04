%import textio

; Tests variable + and - with offsets 1, 2, 3, 8, 33.
; These exercise the candidate addq/subq path:
;   1  -> INC (already special-cased in IRCodeGen.addConstByteToReg)
;   2  -> INC INC (already special-cased)
;   3..8 -> should be ADDQ/SUBQ (NOT YET - currently 4-byte ADD #N/SUB #N)
;   33 -> regular ADD/SUB #33 (fallback for values > 8)
;
; With v = 100 (ubyte, @shared to prevent const folding):
;   v + 1  = 101   v - 1  =  99
;   v + 2  = 102   v - 2  =  98
;   v + 3  = 103   v - 3  =  97
;   v + 8  = 108   v - 8  =  92
;   v + 33 = 133   v - 33 =  67
;
;   prog8c -target qemu68k -out /tmp/opencode examples/test.p8
;   grep -E 'add\.b|sub\.b|addq|subq' /tmp/opencode/test.p8ir
;   prog8c -target qemu68k -emu examples/test.p8

main {
    sub start() {
        ubyte @shared v = 100
        ubyte @shared v_add1 = v + 1
        ubyte @shared v_add2 = v + 2
        ubyte @shared v_add3 = v + 3
        ubyte @shared v_add8 = v + 8
        ubyte @shared v_add33 = v + 33
        ubyte @shared v_sub1 = v - 1
        ubyte @shared v_sub2 = v - 2
        ubyte @shared v_sub3 = v - 3
        ubyte @shared v_sub8 = v - 8
        ubyte @shared v_sub33 = v - 33

        txt.print("v      = ")
        txt.print_ub(v)
        txt.print(" (expected 100)")
        txt.nl()
        txt.print("v + 1  = ")
        txt.print_ub(v_add1)
        txt.print(" (expected 101)")
        txt.nl()
        txt.print("v + 2  = ")
        txt.print_ub(v_add2)
        txt.print(" (expected 102)")
        txt.nl()
        txt.print("v + 3  = ")
        txt.print_ub(v_add3)
        txt.print(" (expected 103)")
        txt.nl()
        txt.print("v + 8  = ")
        txt.print_ub(v_add8)
        txt.print(" (expected 108)")
        txt.nl()
        txt.print("v + 33 = ")
        txt.print_ub(v_add33)
        txt.print(" (expected 133)")
        txt.nl()
        txt.print("v - 1  = ")
        txt.print_ub(v_sub1)
        txt.print(" (expected  99)")
        txt.nl()
        txt.print("v - 2  = ")
        txt.print_ub(v_sub2)
        txt.print(" (expected  98)")
        txt.nl()
        txt.print("v - 3  = ")
        txt.print_ub(v_sub3)
        txt.print(" (expected  97)")
        txt.nl()
        txt.print("v - 8  = ")
        txt.print_ub(v_sub8)
        txt.print(" (expected  92)")
        txt.nl()
        txt.print("v - 33 = ")
        txt.print_ub(v_sub33)
        txt.print(" (expected  67)")
        txt.nl()
    }
}
