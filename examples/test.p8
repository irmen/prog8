%zeropage basicsafe

~ main {

    sub start() {

        greeting()

        ubyte square = stuff.function(12)

        c64scr.print_ub(square)
        c64.CHROUT('\n')

        stuff.name()
        stuff.name()
        stuff.bye()

        abs(4)
        abs(4)
        abs(4)
        abs(4)
        abs(4)
        foobar()
        foobar()
        foobar()
        foobar()


        if(false) {
        } else {

        }


    }


    sub foobar() {
    }

    sub greeting() {
        c64scr.print("hello\n")
    }
}


~ stuff {

    sub function(ubyte v) -> ubyte {
        return v*v
    }

    sub name() {
        c64scr.print("name\n")
    }

    sub bye() {
        c64scr.print("bye\n")
    }

}
