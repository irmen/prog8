%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte[] array= [1,2,3]
        ubyte idx=1
        txt.print_ub(array[idx])
        txt.nl()
        array[idx]+=10
        txt.print_ub(array[idx])
        txt.nl()

        idx=2
        txt.print_ub(idx)
        txt.nl()
        idx+=10
        txt.print_ub(idx)
        txt.nl()
    }
}
