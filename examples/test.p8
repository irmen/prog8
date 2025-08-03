%import textio
%option no_sysinit
%zeropage basicsafe

main {
    ; TODO pointers should also be allocated in zeropage if possible
    ^^bool bptr = 3000
    ^^ubyte ubptr = 3100
    ^^uword wptr = 3200

    sub start() {
        bptr^^ = true
        txt.print_bool(bptr^^)
        bptr^^ = false
        txt.print_bool(bptr^^)
    }
}
