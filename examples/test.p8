%import syslib
; %import graphics
%import textio
%zeropage basicsafe


main {

    sub start()  {

        ubyte v = 1
        @($c000+v) = 10

        txt.print_ub(@($c001))
        txt.chrout('\n')

        @($c000+v) ++
        txt.print_ub(@($c001))
        txt.chrout('\n')

        @($c000+v) += 10
        txt.print_ub(@($c001))
        txt.chrout('\n')

        @($c000+v) *= 10
        txt.print_ub(@($c001))
        txt.chrout('\n')

        ; @($c000) *= 99        ; TODO implement

    }
}
