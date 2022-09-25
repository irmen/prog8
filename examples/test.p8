%import textio
%zeropage basicsafe

main {

    sub start() {

        ubyte v1 = 1
        uword v2 = 1

        ubyte counterb
        uword counter

        repeat v1-1 {
            txt.print("!")
        }

        repeat v2-1 {
            txt.print("?")
        }

        for counterb in 0 to v1 {
            txt.print("y1")
        }
        for counter in 0 to v2 {
            txt.print("y2")
        }

        repeat v1 {
            txt.print("ok1")
        }

        repeat v2 {
            txt.print("ok2")
        }

        repeat v1-1 {
            txt.print("!")
        }

        repeat v2-1 {
            txt.print("?")
        }

        while v1-1 {
            txt.print("%")
        }

        while v2-1 {
            txt.print("*")
        }


        for counterb in 0 to v1-1 {
            txt.print("@")
        }
        for counter in 0 to v2-1 {
            txt.print("y#")
        }

        repeat 0 {
            txt.print("zero1")
        }
        repeat $0000 {
            txt.print("zero2")
        }
    }
}

