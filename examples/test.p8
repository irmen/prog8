%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        float @shared fl1 = 4444.234
        float @shared fl2 = -9999.111
        float @shared fl3 = fl1+fl2
        floats.print(fl1)
        txt.spc()
        floats.print(fl2)
        txt.spc()
        floats.print(fl3)
        txt.nl()
        txt.print_w(fl3 as word)
        txt.nl()
    }
}
