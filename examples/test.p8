%zeropage basicsafe

~ main {

    sub start() {

        ubyte[3] array1
        str string1="hello"

        uword x = &array1

        word sl = len(@( &string1 ))

        c64scr.print_w(sl)      ; should be 5
    }
}
