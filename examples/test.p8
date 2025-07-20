%option no_sysinit
%zeropage kernalsafe
%import textio
%zpallowed 224,255

main {
    uword @shared @requirezp var1 = 0
    uword @shared @requirezp var2 = 0
    uword @shared @requirezp var3 = 0
    uword @shared @requirezp var4 = 0
    uword @shared @requirezp var5 = 0

    sub start() {
        txt.print_uw(var1)
        txt.spc()
        txt.print_uw(var2)
        txt.spc()
        txt.print_uw(var3)
        txt.spc()
        txt.print_uw(var4)
        txt.spc()
        txt.print_uw(var5)
        txt.nl()

        repeat {}
    }
}
