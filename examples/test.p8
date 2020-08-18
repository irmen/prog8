%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
        uword count=0


        count=0
        repeat 10 {
            count++
        }
        c64scr.print_uw(count)
        c64.CHROUT('\n')

        count=0
        repeat 255 {
            count++
        }
        c64scr.print_uw(count)
        c64.CHROUT('\n')

        count=0
        repeat 256 {
            count++
        }
        c64scr.print_uw(count)
        c64.CHROUT('\n')

        count=0
        repeat 40000 {
            count++
        }
        c64scr.print_uw(count)
        c64.CHROUT('\n')

    }
}
