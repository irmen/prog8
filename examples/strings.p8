%import c64lib
%zeropage basicsafe

main {

sub start() {

    str s1 = "apple"
    str s2 = "banana"
    byte[] a1 = [66,77,88,0]
    ubyte i1 = 101
    uword w1 = 000

    c64.STROUT(s1)
    c64.CHROUT('\n')
    c64.STROUT(a1)
    c64.CHROUT('\n')

    c64scr.print("will play the music from boulderdash,\nmade in 1984 by peter liepa.\npress enter to start: ")     ; XXX TODO FIX PRINT OUTPUT

;    c64scr.print_uwhex(s1, true)
;    w1 = &s1
;    c64scr.print_uwhex(w1, true)
;
;    c64scr.print_uwhex(a1, true)
;    w1 = &a1
;    c64scr.print_uwhex(w1, true)
;
;    s1 = s1
;    s1 = s2
;    s2 = "zzz"

}
}


