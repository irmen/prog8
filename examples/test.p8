%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {

        uword zzz=memory("derp", 2000)
        txt.print("hello")
    }
}
