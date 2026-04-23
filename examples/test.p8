%import textio
%zeropage basicsafe

main {
    const ^^uword bptr = $2000
    const ^^ubyte byteptr = $2000

    sub start() {
        txt.print("ptr=")
        txt.print_uwhex(bptr, true)
        txt.nl()

        uword ptr2 = bptr + 4     ; should be equivalent to $2008  but is not yet const-folded
        txt.print("ptr2=")
        txt.print_uwhex(ptr2, true)     ; should print $2008
        txt.nl()

        cx16.r0 = bptr[$1000]      ; should be equivalent to peekw($4000) but is not yet const-folded
        cx16.r1L = byteptr[$1000]      ; should be equivalent to peek($3000) but is not yet const-folded
    }
}
