%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe



main {

    sub jumpsub() {

        ; goto jumpsub        ; TODO fix compiler loop
        goto blabla
blabla:
        A=99
        return

    }

    sub start() {

        A <<= 2
        A >>= 2
        A -= 3
        A = A+A
        lsl(X)
        lsl(Y)
        lsl(A)
        lsl(@($d020))
    }

}


