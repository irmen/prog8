%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


main {

    ; TODO check removal of empty loops



    sub start() {
        ubyte i
        for i in 1 to 20 {
            c64.CHROUT('*')
        }
        c64.CHROUT('\n')

        i=0
        do {
            c64.CHROUT('*')
            i++
        } until i==20
        c64.CHROUT('\n')

        repeat {
            break
            continue
            c64.CHROUT('*')
        }
        c64.CHROUT('\n')

        repeat 0 {
            c64.CHROUT('@')
            break
            continue
        }
        c64.CHROUT('\n')

        repeat 1 {
            c64.CHROUT('1')
            continue
            break
        }
        c64.CHROUT('\n')

        repeat 255 {
            c64.CHROUT('@')
        }
        c64.CHROUT('\n')
        repeat 256 {
            c64.CHROUT('!')
        }
        c64.CHROUT('\n')

        uword teller
        repeat 4000 {
            teller++
        }
        c64scr.print_uw(teller)
        c64.CHROUT('\n')


        repeat {
        }

     }
}
