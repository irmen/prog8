%import syslib
; %import graphics
%import textio
%zeropage basicsafe


main $0900{

    sub start()  {

        ubyte x= 1
        do {
            ubyte v = x
            x++
        } until v==0

        ; @($c000) *= 99        ; TODO implement

    }
}
