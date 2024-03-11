%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword @shared ref = $2000
        ref[5]=10
        txt.print_ub(ref[5])
        txt.spc()
        ref[5]--
        txt.print_ub(ref[5])
        txt.spc()
        ref[5]++
        txt.print_ub(ref[5])
        txt.nl()
        ref[5]-=2
        txt.print_ub(ref[5])
        txt.spc()
        ref[5]+=2
        txt.print_ub(ref[5])
        txt.nl()
        ref[5]-=3
        txt.print_ub(ref[5])
        txt.spc()
        ref[5]+=3
        txt.print_ub(ref[5])
        txt.nl()


        ubyte[] array = [1,2,3,4,5,10]
        array[5]=10
        txt.print_ub(array[5])
        txt.spc()
        array[5]--
        txt.print_ub(array[5])
        txt.spc()
        array[5]++
        txt.print_ub(array[5])
        txt.nl()
        array[5]-=2
        txt.print_ub(array[5])
        txt.spc()
        array[5]+=2
        txt.print_ub(array[5])
        txt.nl()
        array[5]-=3
        txt.print_ub(array[5])
        txt.spc()
        array[5]+=3
        txt.print_ub(array[5])
        txt.nl()


;        cx16.r0L = 5
;        ref[cx16.r0L]=10
;        txt.print_ub(ref[cx16.r0L])
;        txt.spc()
;        ref[cx16.r0L]--
;        txt.print_ub(ref[cx16.r0L])
;        txt.spc()
;        ref[cx16.r0L]++
;        txt.print_ub(ref[cx16.r0L])
;        txt.nl()
;
;        uword @shared uw = 1000
;        word @shared sw = -1000
;
;        txt.print_uw(uw)
;        txt.spc()
;        uw++
;        txt.print_uw(uw)
;        txt.spc()
;        uw--
;        txt.print_uw(uw)
;        txt.nl()
;        uw = $00ff
;        txt.print_uw(uw)
;        txt.spc()
;        uw++
;        txt.print_uw(uw)
;        txt.spc()
;        uw--
;        txt.print_uw(uw)
;        txt.nl()
;
;        txt.print_w(sw)
;        txt.spc()
;        sw++
;        txt.print_w(sw)
;        txt.spc()
;        sw--
;        txt.print_w(sw)
;        txt.nl()
;        sw = $00ff
;        txt.print_w(sw)
;        txt.spc()
;        sw++
;        txt.print_w(sw)
;        txt.spc()
;        sw--
;        txt.print_w(sw)
;        txt.nl()
;        sw = -257
;        txt.print_w(sw)
;        txt.spc()
;        sw++
;        txt.print_w(sw)
;        txt.spc()
;        sw--
;        txt.print_w(sw)
;        txt.nl()

    /*
    seekerRef[SKR_X]--     this code looks very wrong with the pha/pla stuff
    bulletRef[BD_Y]--/++
    enemyRef[EN_MOVE_CNT]--/++

    signed word--/++
    unsigned word--/++

    attackRef+=FIELD_COUNT

*/

    }
}
