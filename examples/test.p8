%import floats
%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte[] array = [ $00, $11, $22, $33, $44, $55, $66, $77, $88, $99, $aa, $bb]

        ubyte x = 2
        ubyte y = 3
        txt.print_uwhex(mkword(array[9], array[8]), true)
        txt.print_uwhex(mkword(array[x*y+y], array[y*x+x]), true)
    }
}

