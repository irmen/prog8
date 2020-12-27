%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit

main {


    sub start () {
        uword length
        uword total = 0

        length=200
        count()
        txt.print_uw(total)
        txt.chrout('\n')
        length=255
        count()
        txt.print_uw(total)
        txt.chrout('\n')
        length=256
        count()
        txt.print_uw(total)
        txt.chrout('\n')
        length=257
        count()
        txt.print_uw(total)
        txt.chrout('\n')
        length=9999
        count()
        txt.print_uw(total)
        txt.chrout('\n')

        test_stack.test()


        sub count() {
            total = 0
            if length>256 {
                repeat length-1
                    total++
            } else {
                uword total2
                repeat lsb(length-1)
                    total++
;                repeat (length-1) as ubyte      ; TODO lsb(length-1) doesn't work!?!?!?
;                    total++
            }
        }
    }


}
