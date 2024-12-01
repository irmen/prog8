%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared index
        bool @shared success = index==0 or index==255
        if success
            txt.print("yo")
        if index==0 or index==255
            txt.print("yo")
    }
}
