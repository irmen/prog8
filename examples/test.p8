%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte uu
        cx16.rambank(1)
        sys.memcopy(&banked.double, $a000, 100)
        cx16.rambank(0)
        txt.nl()

        uword ww
        uu = 99
        txt.print_ub(uu)
        txt.nl()
        callfar($01, $a000, &uu)
        txt.print_ub(uu)

        uu = '\n'
        callrom($00, $ffd2, &uu)
        uu = 'a'
        callrom($00, $ffd2, &uu)
        uu = '!'
        callrom($00, $ffd2, &uu)
        uu = '\n'
        callrom($00, $ffd2, &uu)

;        cx16.rombank(0)
;        %asm{{
;            lda  #13
;            jsr  $ffd2
;            lda  #'a'
;            jsr  $ffd2
;            lda  #13
;            jsr  $ffd2
;        }}
;        cx16.rombank(4)
    }
}


banked {
    asmsub double(ubyte number @A) -> ubyte @A {
        %asm {{
            asl  a
            rts
        }}
    }
}
