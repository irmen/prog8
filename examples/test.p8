%import textio
%zeropage basicsafe
%encoding petscii

main {
    sub start() {
        bool bb
        ubyte ub
        uword uw

        uw = 9999
        txt.print_uw(uw)
        txt.spc()
        uw, void = thing2()
        txt.print_uw(uw)
        txt.nl()

        uw = 9999
        txt.print_uw(uw)
        txt.spc()
        uw, bb = thing2()
        txt.print_uw(uw)
        txt.nl()

;        uw = 9999
;        txt.print_uw(uw)
;        txt.spc()
;        uw, ub = thing2()
;        txt.print_uw(uw)
;        txt.nl()
    }

    sub thing2() -> ubyte, bool {
        cx16.r0L=42
        return cx16.r0L, cx16.r0L==0
    }
;    asmsub thing2() -> ubyte @A, bool @Pc {
;        %asm {{
;            lda  #42
;            sec
;            rts
;        }}
;    }
}
