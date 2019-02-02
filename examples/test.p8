%zeropage basicsafe


~ main {

        ; @todo fucks up basic - a few list: will corrupt the interpreter

    ubyte dummy
    ubyte dummy2

    sub start() {
        ubyte qq
        ubyte qq2
        ubyte qq3
        ubyte qq4
        ubyte qq5
        c64scr.setcc(13, 10, 89, 11)
    }

}
