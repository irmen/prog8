%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword[] @split splitarray = [1000,2000,3000]
        uword[] @nosplit nosplitarray = [1001,2002,3003]
        uword[] wordarray = [1111,2222,3333]

        txt.print_uw(splitarray[2])
        txt.spc()
        txt.print_uw(nosplitarray[2])
        txt.spc()
        txt.print_uw(wordarray[2])
        txt.nl()
    }
}
