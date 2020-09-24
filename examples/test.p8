%import syslib
; %import graphics
%import textio
%zeropage basicsafe


main {

    sub start()  {

        word x= 1

        if x==10 {
            x=99
        } else {
            x=100
        }

        if x==10 {
            ;nothing
        } else {
            x=100
        }

        if x==10 {
            x=99
        } else {
            ; nothing
        }

        while 1==x {
            txt.print_w(x)
            txt.chrout('\n')
            x++
        }
        txt.chrout('\n')

        x=0
        do {
            x++
            txt.print_w(x)
            txt.chrout('\n')
        } until x==10

        ; @($c000) *= 99        ; TODO implement

    }
}
