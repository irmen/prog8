%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        alias print = txt.print
        alias zz=print
        zz("chained")
    }
}
