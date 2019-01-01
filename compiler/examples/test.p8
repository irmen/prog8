%import c64utils

~ main {

    sub start()  {
        uword uw1 = 0
        uword uw2 = $77ff
        uword uw3 = $55aa
        word w1 = 0
        word w2 = $22ff
        word w3 = $55aa
        memory uword muw1 = $2000
        memory uword muw2 = $3000
        memory uword muw3 = $4000
        memory word mw1 = $4100
        memory word mw2 = $4200
        memory word mw3 = $4300

        uword[3] uwarr = $55aa
        word[3] warr = $55aa
        memory uword[3] muwarr = $4400
        memory word[3] mwarr = $4500

        muw3 = $55aa
        uwarr[0] = $55aa
        uwarr[1] = $55aa
        uwarr[2] = $55aa
        muwarr[0] = $55aa
        muwarr[1] = $55aa
        muwarr[2] = $55aa
        mwarr[0] = $55aa
        mwarr[1] = $55aa
        mwarr[2] = $55aa

        uw1 = uw2 + $55aa       ;52649
        c64scr.print_uw(uw1)
        c64.CHROUT('\n')

        uw1 = uw2 + uw3       ;52649
        c64scr.print_uw(uw1)
        c64.CHROUT('\n')

        uw1 = uw2 + muw3       ;52649
        c64scr.print_uw(uw1)
        c64.CHROUT('\n')

;        uw1 = uw2 + uwarr[2]       ;52649
;        c64scr.print_uw(uw1)
;        c64.CHROUT('\n')


;        w1 = w2 + $55aa     ; 30889
;        c64scr.print_w(w1)
;        c64.CHROUT('\n')
;        w1 = w2 + w3     ; 30889
;        c64scr.print_w(w1)
;        c64.CHROUT('\n')
;
;        uwarr[2] = uwarr[1] + $55aa
;        uwarr[2] = uwarr[1] + uw3
;        uwarr[2] = uwarr[1] + uwarr[1]
    }
}
