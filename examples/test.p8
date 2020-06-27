%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe



main {

    sub ss(ubyte qq) {
        Y=qq
    }

    sub tiny() {
        Y++
    }

    sub tiny2() {
        for A in 10 to 20 {
            ubyte xx = A
        }
    }

    sub start() {
        uword zomg=2
        A=lsb(zomg)
        ss(100)
        ss(101)
        tiny()
        tiny()
        tiny2()
        tiny2()
    }

}


