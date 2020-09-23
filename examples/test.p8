%import syslib
; %import graphics
%import textio
%zeropage basicsafe


main $0900{

    sub start()  {

        do {
            ubyte v = 1
        } until v==0

        ; @($c000) *= 99        ; TODO implement

    }
}
