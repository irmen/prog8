%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword[8] @nosplit warr
        uword[8] @split swarr

        uword @shared ptr = $1000
        const uword cptr = $2000
        txt.print_uwhex(&ptr[2], true)
        txt.spc()
        txt.print_uwhex(&cptr[2], true)
        txt.nl()

        txt.print_uwhex(&warr, true)
        txt.spc()
        txt.print_uwhex(&warr[2], true)
        txt.nl()

        txt.print("addresses of split word array:\n")
        txt.print_uwhex(&swarr, true)
        txt.spc()
        txt.print_uwhex(&<swarr, true)
        txt.spc()
        txt.print_uwhex(&>swarr, true)
        txt.nl()
        txt.print("addresses of normal word array:\n")
        txt.print_uwhex(&warr, true)
        txt.spc()
        txt.print_uwhex(&<warr, true)
        txt.nl()

        txt.print("addresses of split word array element 4:\n")
        txt.print_uwhex(&swarr[4], true)
        txt.spc()
        txt.print_uwhex(&<swarr[4], true)
        txt.spc()
        txt.print_uwhex(&>swarr[4], true)
        txt.nl()
        txt.print("addresses of split word array element 4 via var:\n")
        cx16.r0L=4
        txt.print_uwhex(&swarr[cx16.r0L], true)
        txt.spc()
        txt.print_uwhex(&<swarr[cx16.r0L], true)
        txt.spc()
        txt.print_uwhex(&>swarr[cx16.r0L], true)
        txt.nl()
        txt.print("addresses of normal word array element 4:\n")
        txt.print_uwhex(&warr[4], true)
        txt.spc()
        txt.print_uwhex(&<warr[4], true)
        txt.nl()
    }
}
