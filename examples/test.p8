%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared xx=1
        uword @shared yy

        ubyte[16] array
        array[1] = 1

        xx += 3
        yy += 3
        xx -= 3
        yy -= 3

        txt.print_ub(array[1])
        txt.spc()
        array[1]++
        txt.print_ub(array[1])
        txt.spc()
        array[1]--
        txt.print_ub(array[1])
        txt.nl()

        txt.print_ub(array[1])
        txt.spc()
        array[xx]++
        txt.print_ub(array[1])
        txt.spc()
        array[xx]--
        txt.print_ub(array[1])
        txt.nl()

    }
}
